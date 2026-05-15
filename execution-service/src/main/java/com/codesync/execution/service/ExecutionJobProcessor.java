package com.codesync.execution.service;

import com.codesync.execution.entity.ExecutionJob;
import com.codesync.execution.entity.ExecutionStatus;
import com.codesync.execution.repository.ExecutionJobRepository;
import com.codesync.execution.runtime.ExecutionEngine;
import com.codesync.execution.runtime.ExecutionOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ExecutionJobProcessor {

    private final ExecutionJobRepository repository;
    private final ExecutionEngine executionEngine;
    private final ExecutionEventPublisher executionEventPublisher;

    public ExecutionJobProcessor(
            ExecutionJobRepository repository,
            ExecutionEngine executionEngine,
            ExecutionEventPublisher executionEventPublisher) {
        this.repository = repository;
        this.executionEngine = executionEngine;
        this.executionEventPublisher = executionEventPublisher;
    }

    public void processQueuedJob(UUID jobId) {
        ExecutionJob job = markRunning(jobId);
        if (job == null) {
            return;
        }

        executionEventPublisher.publishStatus(jobId, ExecutionStatus.RUNNING, "Execution started", null, null, null);

        try {
            ExecutionOutcome outcome = executionEngine.execute(
                    job,
                    (stream, chunk) -> executionEventPublisher.publishChunk(jobId, stream, ExecutionStatus.RUNNING, chunk)
            );
            completeJob(jobId, outcome);
        } catch (Exception ex) {
            failJob(jobId, ex);
        }
    }

    @Transactional
    protected ExecutionJob markRunning(UUID jobId) {
        return repository.findByJobId(jobId)
                .map(job -> {
                    if (job.getStatus() != ExecutionStatus.QUEUED) {
                        return null;
                    }
                    job.setStatus(ExecutionStatus.RUNNING);
                    return repository.save(job);
                })
                .orElse(null);
    }

    @Transactional
    protected void completeJob(UUID jobId, ExecutionOutcome outcome) {
        repository.findByJobId(jobId).ifPresent(job -> {
            ExecutionStatus finalStatus = job.getStatus() == ExecutionStatus.CANCELLED
                    ? ExecutionStatus.CANCELLED
                    : outcome.status();

            job.setStatus(finalStatus);
            job.setStdout(outcome.stdout());
            job.setStderr(outcome.stderr());
            job.setExitCode(outcome.exitCode());
            job.setExecutionTimeMs(outcome.executionTimeMs());
            job.setMemoryUsedKb(outcome.memoryUsedKb());
            if (job.getCompletedAt() == null) {
                job.setCompletedAt(LocalDateTime.now());
            }
            repository.save(job);

            executionEventPublisher.publishStatus(
                    jobId,
                    job.getStatus(),
                    terminalMessage(job.getStatus()),
                    job.getExitCode(),
                    job.getExecutionTimeMs(),
                    job.getMemoryUsedKb()
            );
        });
    }

    @Transactional
    protected void failJob(UUID jobId, Exception ex) {
        repository.findByJobId(jobId).ifPresent(job -> {
            if (job.getStatus() != ExecutionStatus.CANCELLED) {
                job.setStatus(ExecutionStatus.FAILED);
                job.setStderr(ex.getMessage());
                job.setExitCode(-1);
            }
            if (job.getCompletedAt() == null) {
                job.setCompletedAt(LocalDateTime.now());
            }
            repository.save(job);

            executionEventPublisher.publishStatus(
                    jobId,
                    job.getStatus(),
                    job.getStatus() == ExecutionStatus.CANCELLED ? "Execution cancelled" : "Execution failed",
                    job.getExitCode(),
                    job.getExecutionTimeMs(),
                    job.getMemoryUsedKb()
            );
        });
    }

    private String terminalMessage(ExecutionStatus status) {
        return switch (status) {
            case COMPLETED -> "Execution completed";
            case FAILED -> "Execution failed";
            case TIMED_OUT -> "Execution timed out";
            case CANCELLED -> "Execution cancelled";
            case QUEUED -> "Execution queued";
            case RUNNING -> "Execution running";
        };
    }
}
