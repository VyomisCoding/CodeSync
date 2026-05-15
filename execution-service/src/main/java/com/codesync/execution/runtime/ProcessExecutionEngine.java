package com.codesync.execution.runtime;

import com.codesync.execution.entity.ExecutionJob;
import com.codesync.execution.entity.ExecutionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProcessExecutionEngine implements ExecutionEngine {

    private static final Pattern JAVA_CLASS_PATTERN =
            Pattern.compile("public\\s+class\\s+([A-Za-z_$][A-Za-z\\d_$]*)");

    private final long maxExecutionTimeMs;
    private final int maxMemoryMb;
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private final Map<UUID, AtomicBoolean> cancellationSignals = new ConcurrentHashMap<>();
    private final Map<UUID, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<ExecutionLanguage, String> versionCache = new ConcurrentHashMap<>();

    public ProcessExecutionEngine(
            @Value("${codesync.execution.max-execution-time-ms:10000}") long maxExecutionTimeMs,
            @Value("${codesync.execution.max-memory-mb:256}") int maxMemoryMb) {
        this.maxExecutionTimeMs = maxExecutionTimeMs;
        this.maxMemoryMb = maxMemoryMb;
    }

    @Override
    public ExecutionOutcome execute(ExecutionJob job, ExecutionOutputPublisher outputPublisher) {
        ExecutionLanguage language = ExecutionLanguage.from(job.getLanguage());
        Path tempDirectory = null;
        AtomicBoolean cancellationSignal = new AtomicBoolean(false);
        cancellationSignals.put(job.getJobId(), cancellationSignal);

        long startedAt = System.nanoTime();
        try {
            tempDirectory = Files.createTempDirectory("codesync-execution-" + job.getJobId());
            PreparedExecution preparedExecution = prepareExecution(language, job.getSourceCode(), tempDirectory);

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            AtomicLong peakMemoryKb = new AtomicLong(0);

            long remainingTimeMs = maxExecutionTimeMs;
            if (preparedExecution.compileCommand() != null) {
                ProcessResult compileResult = runProcess(
                        job.getJobId(),
                        preparedExecution.compileCommand(),
                        tempDirectory,
                        "",
                        remainingTimeMs,
                        outputPublisher,
                        stdout,
                        stderr,
                        peakMemoryKb
                );

                remainingTimeMs = maxExecutionTimeMs - toElapsedMs(startedAt);
                if (compileResult.status() != ExecutionStatus.COMPLETED) {
                    return new ExecutionOutcome(
                            compileResult.status(),
                            stdout.toString(),
                            stderr.toString(),
                            compileResult.exitCode(),
                            toElapsedMs(startedAt),
                            peakMemoryKb.get()
                    );
                }
            }

            ProcessResult runResult = runProcess(
                    job.getJobId(),
                    preparedExecution.runCommand(),
                    tempDirectory,
                    job.getStdin(),
                    Math.max(1, remainingTimeMs),
                    outputPublisher,
                    stdout,
                    stderr,
                    peakMemoryKb
            );

            return new ExecutionOutcome(
                    runResult.status(),
                    stdout.toString(),
                    stderr.toString(),
                    runResult.exitCode(),
                    toElapsedMs(startedAt),
                    peakMemoryKb.get()
            );
        } catch (IOException ex) {
            return new ExecutionOutcome(
                    ExecutionStatus.FAILED,
                    "",
                    ex.getMessage(),
                    -1,
                    toElapsedMs(startedAt),
                    0L
            );
        } finally {
            cancellationSignals.remove(job.getJobId());
            runningProcesses.remove(job.getJobId());
            if (tempDirectory != null) {
                deleteRecursively(tempDirectory);
            }
        }
    }

    @Override
    public boolean cancel(UUID jobId) {
        AtomicBoolean signal = cancellationSignals.get(jobId);
        if (signal == null) {
            return false;
        }

        signal.set(true);
        Process process = runningProcesses.get(jobId);
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        return true;
    }

    @Override
    public List<String> getSupportedLanguages() {
        return ExecutionLanguage.supportedNames();
    }

    @Override
    public String getLanguageVersion(String language) {
        ExecutionLanguage executionLanguage = ExecutionLanguage.from(language);
        return versionCache.computeIfAbsent(executionLanguage, this::detectLanguageVersion);
    }

    @PreDestroy
    public void shutdown() {
        ioExecutor.shutdownNow();
    }

    private String detectLanguageVersion(ExecutionLanguage language) {
        ProcessBuilder processBuilder = new ProcessBuilder(language.versionCommand());
        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "unavailable";
            }

            String stdout = readProcessOutput(process.getInputStream());
            String stderr = readProcessOutput(process.getErrorStream());
            String combined = (stdout + "\n" + stderr).trim();
            if (combined.isBlank()) {
                return "unavailable";
            }
            return combined.lines().findFirst().orElse("unavailable");
        } catch (Exception ex) {
            return "unavailable";
        }
    }

    private PreparedExecution prepareExecution(ExecutionLanguage language, String sourceCode, Path directory) throws IOException {
        return switch (language) {
            case JAVA -> prepareJavaExecution(sourceCode, directory);
            case PYTHON -> preparePythonExecution(sourceCode, directory);
            case JAVASCRIPT -> prepareJavascriptExecution(sourceCode, directory);
            case C -> prepareCExecution(sourceCode, directory);
            case CPP -> prepareCppExecution(sourceCode, directory);
        };
    }

    private PreparedExecution prepareJavaExecution(String sourceCode, Path directory) throws IOException {
        String className = resolveJavaClassName(sourceCode);
        Path sourceFile = directory.resolve(className + ".java");
        Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);
        return new PreparedExecution(
                List.of("javac", "-J-Xmx" + maxMemoryMb + "m", sourceFile.getFileName().toString()),
                List.of("java", "-Xmx" + maxMemoryMb + "m", "-cp", directory.toString(), className)
        );
    }

    private PreparedExecution preparePythonExecution(String sourceCode, Path directory) throws IOException {
        Path sourceFile = directory.resolve("main.py");
        Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);
        return new PreparedExecution(
                null,
                List.of("python3", sourceFile.getFileName().toString())
        );
    }

    private PreparedExecution prepareJavascriptExecution(String sourceCode, Path directory) throws IOException {
        Path sourceFile = directory.resolve("main.js");
        Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);
        return new PreparedExecution(
                null,
                List.of("nodejs", "--max-old-space-size=" + maxMemoryMb, sourceFile.getFileName().toString())
        );
    }

    private PreparedExecution prepareCExecution(String sourceCode, Path directory) throws IOException {
        Path sourceFile = directory.resolve("main.c");
        Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);
        return new PreparedExecution(
                List.of("gcc", sourceFile.getFileName().toString(), "-O2", "-o", "program"),
                List.of(resolveExecutable(directory, "program"))
        );
    }

    private PreparedExecution prepareCppExecution(String sourceCode, Path directory) throws IOException {
        Path sourceFile = directory.resolve("main.cpp");
        Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);
        return new PreparedExecution(
                List.of("g++", sourceFile.getFileName().toString(), "-std=c++17", "-O2", "-o", "program"),
                List.of(resolveExecutable(directory, "program"))
        );
    }

    private ProcessResult runProcess(
            UUID jobId,
            List<String> command,
            Path directory,
            String stdin,
            long timeoutMs,
            ExecutionOutputPublisher outputPublisher,
            StringBuilder stdoutCollector,
            StringBuilder stderrCollector,
            AtomicLong peakMemoryKb
    ) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(directory.toFile());
        processBuilder.environment().put("TMPDIR", directory.toString());
        processBuilder.environment().put("TMP", directory.toString());
        processBuilder.environment().put("TEMP", directory.toString());

        Process process = processBuilder.start();
        runningProcesses.put(jobId, process);

        CompletableFuture<Void> stdinWriter = CompletableFuture.runAsync(() -> writeStdin(process, stdin), ioExecutor);
        CompletableFuture<String> stdoutFuture = captureStream(
                process.getInputStream(),
                "STDOUT",
                outputPublisher,
                stdoutCollector
        );
        CompletableFuture<String> stderrFuture = captureStream(
                process.getErrorStream(),
                "STDERR",
                outputPublisher,
                stderrCollector
        );

        long deadlineNanos = System.nanoTime() + Duration.ofMillis(timeoutMs).toNanos();
        boolean finished = false;
        try {
            while (!finished) {
                updatePeakMemory(process.pid(), peakMemoryKb);

                if (Boolean.TRUE.equals(cancellationSignals.get(jobId).get())) {
                    destroyProcess(process);
                    stdinWriter.join();
                    stdoutFuture.join();
                    stderrFuture.join();
                    return new ProcessResult(ExecutionStatus.CANCELLED, null);
                }

                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    destroyProcess(process);
                    stdinWriter.join();
                    stdoutFuture.join();
                    stderrFuture.join();
                    return new ProcessResult(ExecutionStatus.TIMED_OUT, null);
                }

                finished = process.waitFor(Math.min(TimeUnit.NANOSECONDS.toMillis(remainingNanos), 200), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            destroyProcess(process);
            stdinWriter.join();
            stdoutFuture.join();
            stderrFuture.join();
            return new ProcessResult(ExecutionStatus.CANCELLED, null);
        } finally {
            runningProcesses.remove(jobId);
        }

        stdinWriter.join();
        stdoutFuture.join();
        stderrFuture.join();
        updatePeakMemory(process.pid(), peakMemoryKb);

        AtomicBoolean cancellationSignal = cancellationSignals.get(jobId);
        if (cancellationSignal != null && cancellationSignal.get()) {
            return new ProcessResult(ExecutionStatus.CANCELLED, process.isAlive() ? null : process.exitValue());
        }

        int exitCode = process.exitValue();
        ExecutionStatus status = exitCode == 0 ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED;
        return new ProcessResult(status, exitCode);
    }

    private CompletableFuture<String> captureStream(
            InputStream inputStream,
            String streamName,
            ExecutionOutputPublisher outputPublisher,
            StringBuilder collector
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (collector) {
                        if (!collector.isEmpty()) {
                            collector.append(System.lineSeparator());
                        }
                        collector.append(line);
                    }
                    outputPublisher.publish(streamName, line);
                }
            } catch (IOException ex) {
                synchronized (collector) {
                    if (!collector.isEmpty()) {
                        collector.append(System.lineSeparator());
                    }
                    collector.append(ex.getMessage());
                }
            }
            return collector.toString();
        }, ioExecutor);
    }

    private void writeStdin(Process process, String stdin) {
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            if (stdin != null && !stdin.isBlank()) {
                writer.write(stdin);
            }
            writer.flush();
        } catch (IOException ignored) {
        }
    }

    private void updatePeakMemory(long pid, AtomicLong peakMemoryKb) {
        Path statusFile = Path.of("/proc", String.valueOf(pid), "status");
        if (!Files.exists(statusFile)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(statusFile, StandardCharsets.UTF_8)) {
                if (line.startsWith("VmRSS:")) {
                    String value = line.replace("VmRSS:", "").replace("kB", "").trim();
                    peakMemoryKb.updateAndGet(existing -> Math.max(existing, Long.parseLong(value)));
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void destroyProcess(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private String resolveJavaClassName(String sourceCode) {
        Matcher matcher = JAVA_CLASS_PATTERN.matcher(sourceCode);
        return matcher.find() ? matcher.group(1) : "Main";
    }

    private String resolveExecutable(Path directory, String baseName) {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return directory.resolve(baseName + ".exe").toString();
        }
        return "./" + baseName;
    }

    private void deleteRecursively(Path directory) {
        try (var pathStream = Files.walk(directory)) {
            pathStream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private long toElapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String readProcessOutput(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return String.join(System.lineSeparator(), lines);
        }
    }

    private record PreparedExecution(
            List<String> compileCommand,
            List<String> runCommand
    ) {
    }

    private record ProcessResult(
            ExecutionStatus status,
            Integer exitCode
    ) {
    }
}
