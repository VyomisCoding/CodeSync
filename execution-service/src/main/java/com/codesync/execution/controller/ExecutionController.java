package com.codesync.execution.controller;

import com.codesync.execution.dto.ApiResponse;
import com.codesync.execution.dto.ExecutionJobResponse;
import com.codesync.execution.dto.ExecutionResultResponse;
import com.codesync.execution.dto.ExecutionStatsResponse;
import com.codesync.execution.dto.ExecutionSubmitRequest;
import com.codesync.execution.dto.LanguageVersionResponse;
import com.codesync.execution.exception.ForbiddenException;
import com.codesync.execution.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/executions")
@Tag(name = "Execution Service", description = "Queued code execution, results, and streaming APIs")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit a code execution job", security = @SecurityRequirement(name = "bearerAuth"))
    public ExecutionJobResponse submitExecution(
            @Valid @RequestBody ExecutionSubmitRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return executionService.submitExecution(currentUserId(authentication), authorizationHeader, request);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get execution job metadata by jobId", security = @SecurityRequirement(name = "bearerAuth"))
    public ExecutionJobResponse getJobById(
            @PathVariable UUID jobId,
            Authentication authentication) {
        return executionService.getJobById(jobId, currentUserId(authentication), isAdmin(authentication));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List executions for a user", security = @SecurityRequirement(name = "bearerAuth"))
    public List<ExecutionJobResponse> getExecutionsByUser(
            @PathVariable Long userId,
            Authentication authentication) {
        return executionService.getExecutionsByUser(userId, currentUserId(authentication), isAdmin(authentication));
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "List executions for a project", security = @SecurityRequirement(name = "bearerAuth"))
    public List<ExecutionJobResponse> getExecutionsByProject(
            @PathVariable Long projectId,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return executionService.getExecutionsByProject(projectId, currentUserId(authentication), authorizationHeader);
    }

    @PostMapping("/cancel/{jobId}")
    @Operation(summary = "Cancel an execution job", security = @SecurityRequirement(name = "bearerAuth"))
    public ExecutionJobResponse cancelExecution(
            @PathVariable UUID jobId,
            Authentication authentication) {
        return executionService.cancelExecution(jobId, currentUserId(authentication), isAdmin(authentication));
    }

    @GetMapping("/result/{jobId}")
    @Operation(summary = "Get execution result output", security = @SecurityRequirement(name = "bearerAuth"))
    public ExecutionResultResponse getExecutionResult(
            @PathVariable UUID jobId,
            Authentication authentication) {
        return executionService.getExecutionResult(jobId, currentUserId(authentication), isAdmin(authentication));
    }

    @GetMapping("/supportedLanguages")
    @Operation(summary = "List supported execution languages", security = @SecurityRequirement(name = "bearerAuth"))
    public List<String> getSupportedLanguages() {
        return executionService.getSupportedLanguages();
    }

    @GetMapping("/languageVersion/{language}")
    @Operation(summary = "Get the installed runtime/compiler version for a language", security = @SecurityRequirement(name = "bearerAuth"))
    public LanguageVersionResponse getLanguageVersion(@PathVariable String language) {
        return executionService.getLanguageVersion(language);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get execution statistics for the current user", security = @SecurityRequirement(name = "bearerAuth"))
    public ExecutionStatsResponse getStats(Authentication authentication) {
        return executionService.getExecutionStats(currentUserId(authentication));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ForbiddenException("Authentication is required");
        }
        return Long.valueOf(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
