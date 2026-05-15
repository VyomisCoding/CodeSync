package com.codesync.execution.service;

import com.codesync.execution.client.FileAccessClient;
import com.codesync.execution.client.FileSummaryResponse;
import com.codesync.execution.client.ProjectAccessClient;
import com.codesync.execution.client.ProjectSummaryResponse;
import com.codesync.execution.dto.ExecutionJobResponse;
import com.codesync.execution.dto.ExecutionResultResponse;
import com.codesync.execution.dto.ExecutionStatsResponse;
import com.codesync.execution.dto.ExecutionSubmitRequest;
import com.codesync.execution.dto.LanguageVersionResponse;
import com.codesync.execution.entity.ExecutionJob;
import com.codesync.execution.entity.ExecutionStatus;
import com.codesync.execution.exception.BadRequestException;
import com.codesync.execution.exception.ForbiddenException;
import com.codesync.execution.exception.ResourceNotFoundException;
import com.codesync.execution.exception.UnauthorizedException;
import com.codesync.execution.repository.ExecutionJobRepository;
import com.codesync.execution.runtime.ExecutionEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExecutionServiceImpl implements ExecutionService {

    private final ExecutionJobRepository repository;
    private final ExecutionDispatchService executionDispatchService;
    private final ExecutionEngine executionEngine;
    private final ExecutionEventPublisher executionEventPublisher;
    private final ProjectAccessClient projectAccessClient;
    private final FileAccessClient fileAccessClient;
    private final boolean remoteValidationEnabled;

    public ExecutionServiceImpl(
            ExecutionJobRepository repository,
            ExecutionDispatchService executionDispatchService,
            ExecutionEngine executionEngine,
            ExecutionEventPublisher executionEventPublisher,
            ProjectAccessClient projectAccessClient,
            FileAccessClient fileAccessClient,
            @Value("${codesync.execution.remote-validation.enabled:true}") boolean remoteValidationEnabled) {
        this.repository = repository;
        this.executionDispatchService = executionDispatchService;
        this.executionEngine = executionEngine;
        this.executionEventPublisher = executionEventPublisher;
        this.projectAccessClient = projectAccessClient;
        this.fileAccessClient = fileAccessClient;
        this.remoteValidationEnabled = remoteValidationEnabled;
    }

    @Override
    public ExecutionJobResponse submitExecution(Long userId, String authorizationHeader, ExecutionSubmitRequest request) {
        if (userId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        validateExecutionTarget(request.projectId(), request.fileId(), authorizationHeader);

        ExecutionJob job = new ExecutionJob();
        job.setProjectId(request.projectId());
        job.setFileId(request.fileId());
        job.setUserId(userId);
        job.setLanguage(request.language().trim().toLowerCase());
        job.setSourceCode(request.sourceCode());
        job.setStdin(request.stdin() == null ? "" : request.stdin());
        job.setStatus(ExecutionStatus.QUEUED);

        ExecutionJob saved = repository.saveAndFlush(job);
        executionEventPublisher.publishStatus(saved.getJobId(), ExecutionStatus.QUEUED, "Execution queued", null, null, null);
        executionDispatchService.dispatch(saved.getJobId());
        return toJobResponse(saved);
    }

    @Override
    public ExecutionJobResponse getJobById(UUID jobId, Long requesterUserId, boolean isAdmin) {
        ExecutionJob job = requireJob(jobId);
        assertCanReadJob(job, requesterUserId, isAdmin);
        return toJobResponse(job);
    }

    @Override
    public List<ExecutionJobResponse> getExecutionsByUser(Long userId, Long requesterUserId, boolean isAdmin) {
        if (requesterUserId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (!isAdmin && !requesterUserId.equals(userId)) {
            throw new ForbiddenException("You can only view your own executions");
        }

        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toJobResponse)
                .toList();
    }

    @Override
    public List<ExecutionJobResponse> getExecutionsByProject(Long projectId, Long requesterUserId, String authorizationHeader) {
        if (requesterUserId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (remoteValidationEnabled) {
            assertReadableProject(projectId, authorizationHeader);
        }

        return repository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toJobResponse)
                .toList();
    }

    @Override
    public ExecutionJobResponse cancelExecution(UUID jobId, Long requesterUserId, boolean isAdmin) {
        ExecutionJob job = requireJob(jobId);
        assertCanManageJob(job, requesterUserId, isAdmin);

        if (isTerminal(job.getStatus())) {
            return toJobResponse(job);
        }

        executionEngine.cancel(jobId);
        job.setStatus(ExecutionStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        ExecutionJob saved = repository.save(job);
        executionEventPublisher.publishStatus(jobId, ExecutionStatus.CANCELLED, "Execution cancelled", saved.getExitCode(), saved.getExecutionTimeMs(), saved.getMemoryUsedKb());
        return toJobResponse(saved);
    }

    @Override
    public ExecutionResultResponse getExecutionResult(UUID jobId, Long requesterUserId, boolean isAdmin) {
        ExecutionJob job = requireJob(jobId);
        assertCanReadJob(job, requesterUserId, isAdmin);
        return toExecutionResult(job);
    }

    @Override
    public List<String> getSupportedLanguages() {
        return executionEngine.getSupportedLanguages();
    }

    @Override
    public LanguageVersionResponse getLanguageVersion(String language) {
        return new LanguageVersionResponse(language, executionEngine.getLanguageVersion(language));
    }

    @Override
    public ExecutionStatsResponse getExecutionStats(Long requesterUserId) {
        if (requesterUserId == null) {
            throw new UnauthorizedException("Authentication required");
        }

        List<ExecutionJob> jobs = repository.findByUserIdOrderByCreatedAtDesc(requesterUserId);
        Map<ExecutionStatus, Long> counts = new EnumMap<>(ExecutionStatus.class);
        for (ExecutionStatus status : ExecutionStatus.values()) {
            counts.put(status, 0L);
        }
        jobs.forEach(job -> counts.computeIfPresent(job.getStatus(), (status, existing) -> existing + 1));

        return new ExecutionStatsResponse(
                repository.countByUserId(requesterUserId),
                counts.get(ExecutionStatus.QUEUED),
                counts.get(ExecutionStatus.RUNNING),
                counts.get(ExecutionStatus.COMPLETED),
                counts.get(ExecutionStatus.FAILED),
                counts.get(ExecutionStatus.TIMED_OUT),
                counts.get(ExecutionStatus.CANCELLED)
        );
    }

    private void validateExecutionTarget(Long projectId, Long fileId, String authorizationHeader) {
        if (!remoteValidationEnabled) {
            return;
        }
        assertReadableProject(projectId, authorizationHeader);
        assertReadableProjectFile(fileId, projectId, authorizationHeader);
    }

    private ProjectSummaryResponse assertReadableProject(Long projectId, String authorizationHeader) {
        return projectAccessClient.getAccessibleProject(projectId, authorizationHeader);
    }

    private FileSummaryResponse assertReadableProjectFile(Long fileId, Long expectedProjectId, String authorizationHeader) {
        FileSummaryResponse file = fileAccessClient.getReadableFile(fileId, authorizationHeader);
        if (!expectedProjectId.equals(file.projectId())) {
            throw new BadRequestException("fileId does not belong to the supplied projectId");
        }
        if (!"FILE".equalsIgnoreCase(file.type())) {
            throw new BadRequestException("Executions can only target files");
        }
        return file;
    }

    private ExecutionJob requireJob(UUID jobId) {
        return repository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution job not found"));
    }

    private void assertCanReadJob(ExecutionJob job, Long requesterUserId, boolean isAdmin) {
        if (requesterUserId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (isAdmin || requesterUserId.equals(job.getUserId())) {
            return;
        }
        throw new ForbiddenException("You do not have access to this execution job");
    }

    private void assertCanManageJob(ExecutionJob job, Long requesterUserId, boolean isAdmin) {
        assertCanReadJob(job, requesterUserId, isAdmin);
    }

    private boolean isTerminal(ExecutionStatus status) {
        return status == ExecutionStatus.COMPLETED
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.TIMED_OUT
                || status == ExecutionStatus.CANCELLED;
    }

    private ExecutionJobResponse toJobResponse(ExecutionJob job) {
        return new ExecutionJobResponse(
                job.getJobId(),
                job.getProjectId(),
                job.getFileId(),
                job.getUserId(),
                job.getLanguage(),
                job.getStatus(),
                job.getExitCode(),
                job.getExecutionTimeMs(),
                job.getMemoryUsedKb(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }

    private ExecutionResultResponse toExecutionResult(ExecutionJob job) {
        return new ExecutionResultResponse(
                job.getJobId(),
                job.getStatus(),
                job.getStdout(),
                job.getStderr(),
                job.getExitCode(),
                job.getExecutionTimeMs(),
                job.getMemoryUsedKb(),
                job.getCompletedAt()
        );
    }
}
