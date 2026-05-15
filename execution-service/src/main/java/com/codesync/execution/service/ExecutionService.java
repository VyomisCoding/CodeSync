package com.codesync.execution.service;

import com.codesync.execution.dto.ExecutionJobResponse;
import com.codesync.execution.dto.ExecutionResultResponse;
import com.codesync.execution.dto.ExecutionStatsResponse;
import com.codesync.execution.dto.ExecutionSubmitRequest;
import com.codesync.execution.dto.LanguageVersionResponse;

import java.util.List;
import java.util.UUID;

public interface ExecutionService {

    ExecutionJobResponse submitExecution(Long userId, String authorizationHeader, ExecutionSubmitRequest request);

    ExecutionJobResponse getJobById(UUID jobId, Long requesterUserId, boolean isAdmin);

    List<ExecutionJobResponse> getExecutionsByUser(Long userId, Long requesterUserId, boolean isAdmin);

    List<ExecutionJobResponse> getExecutionsByProject(Long projectId, Long requesterUserId, String authorizationHeader);

    ExecutionJobResponse cancelExecution(UUID jobId, Long requesterUserId, boolean isAdmin);

    ExecutionResultResponse getExecutionResult(UUID jobId, Long requesterUserId, boolean isAdmin);

    List<String> getSupportedLanguages();

    LanguageVersionResponse getLanguageVersion(String language);

    ExecutionStatsResponse getExecutionStats(Long requesterUserId);
}
