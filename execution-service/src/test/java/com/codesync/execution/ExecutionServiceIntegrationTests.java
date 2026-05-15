package com.codesync.execution;

import com.codesync.execution.dto.ExecutionJobResponse;
import com.codesync.execution.dto.ExecutionResultResponse;
import com.codesync.execution.dto.ExecutionSubmitRequest;
import com.codesync.execution.entity.ExecutionStatus;
import com.codesync.execution.service.ExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExecutionServiceIntegrationTests {

    @Autowired
    private ExecutionService executionService;

    @Test
    void javaExecutionCompletesSuccessfully() throws Exception {
        ExecutionJobResponse submitted = executionService.submitExecution(
                1001L,
                null,
                new ExecutionSubmitRequest(
                        2001L,
                        3001L,
                        "java",
                        """
                        public class Main {
                            public static void main(String[] args) {
                                System.out.println("hello from execution");
                            }
                        }
                        """,
                        ""
                )
        );

        assertThat(submitted.status()).isEqualTo(ExecutionStatus.QUEUED);

        ExecutionResultResponse result = waitForTerminalResult(submitted.jobId());
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.stdout()).contains("hello from execution");
        assertThat(result.stderr()).isBlank();
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void longRunningExecutionCanBeCancelled() throws Exception {
        ExecutionJobResponse submitted = executionService.submitExecution(
                1001L,
                null,
                new ExecutionSubmitRequest(
                        2001L,
                        3001L,
                        "java",
                        """
                        public class Main {
                            public static void main(String[] args) throws Exception {
                                System.out.println("starting");
                                Thread.sleep(30000);
                                System.out.println("done");
                            }
                        }
                        """,
                        ""
                )
        );

        Thread.sleep(1000);
        ExecutionJobResponse cancelled = executionService.cancelExecution(submitted.jobId(), 1001L, false);
        assertThat(cancelled.status()).isEqualTo(ExecutionStatus.CANCELLED);

        ExecutionResultResponse result = waitForTerminalResult(submitted.jobId());
        assertThat(result.status()).isEqualTo(ExecutionStatus.CANCELLED);
    }

    private ExecutionResultResponse waitForTerminalResult(java.util.UUID jobId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            ExecutionResultResponse result = executionService.getExecutionResult(jobId, 1001L, false);
            if (result.status() == ExecutionStatus.COMPLETED
                    || result.status() == ExecutionStatus.FAILED
                    || result.status() == ExecutionStatus.TIMED_OUT
                    || result.status() == ExecutionStatus.CANCELLED) {
                return result;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Execution job did not finish in time");
    }
}
