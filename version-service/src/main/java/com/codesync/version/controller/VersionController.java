package com.codesync.version.controller;

import com.codesync.version.dto.CreateBranchRequest;
import com.codesync.version.dto.CreateSnapshotRequest;
import com.codesync.version.dto.SnapshotDiffResponse;
import com.codesync.version.dto.SnapshotResponse;
import com.codesync.version.dto.TagSnapshotRequest;
import com.codesync.version.service.VersionService;
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

@RestController
@RequestMapping("/versions")
@Tag(name = "Version Service", description = "Snapshot history, restore, diff, branch, and tag APIs")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    @PostMapping("/create")
    @Operation(summary = "Create a snapshot for a file", security = @SecurityRequirement(name = "bearerAuth"))
    public SnapshotResponse createSnapshot(
            @Valid @RequestBody CreateSnapshotRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.createSnapshot(currentUserId(authentication), authorizationHeader, request);
    }

    @GetMapping("/{snapshotId}")
    @Operation(summary = "Get a snapshot by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public SnapshotResponse getSnapshot(
            @PathVariable Long snapshotId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.getSnapshotById(snapshotId, authorizationHeader);
    }

    @GetMapping("/file/{fileId}")
    @Operation(summary = "List snapshots for a file", security = @SecurityRequirement(name = "bearerAuth"))
    public List<SnapshotResponse> getSnapshotsByFile(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.getSnapshotsByFile(fileId, authorizationHeader);
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "List snapshots for a project", security = @SecurityRequirement(name = "bearerAuth"))
    public List<SnapshotResponse> getSnapshotsByProject(
            @PathVariable Long projectId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.getSnapshotsByProject(projectId, authorizationHeader);
    }

    @GetMapping("/branch/{branch}")
    @Operation(summary = "List snapshots on a branch", security = @SecurityRequirement(name = "bearerAuth"))
    public List<SnapshotResponse> getSnapshotsByBranch(
            @PathVariable String branch,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.getSnapshotsByBranch(branch, authorizationHeader);
    }

    @GetMapping("/latest/{fileId}")
    @Operation(summary = "Get the latest snapshot for a file", security = @SecurityRequirement(name = "bearerAuth"))
    public SnapshotResponse getLatestSnapshot(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.getLatestSnapshot(fileId, authorizationHeader);
    }

    @PostMapping("/restore/{snapshotId}")
    @Operation(summary = "Restore a snapshot by creating a new snapshot and updating the file", security = @SecurityRequirement(name = "bearerAuth"))
    public SnapshotResponse restoreSnapshot(
            @PathVariable Long snapshotId,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.restoreSnapshot(snapshotId, currentUserId(authentication), authorizationHeader);
    }

    @GetMapping("/diff/{snapshotId1}/{snapshotId2}")
    @Operation(summary = "Generate a line diff between two snapshots", security = @SecurityRequirement(name = "bearerAuth"))
    public SnapshotDiffResponse diffSnapshots(
            @PathVariable Long snapshotId1,
            @PathVariable Long snapshotId2,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.diffSnapshots(snapshotId1, snapshotId2, authorizationHeader);
    }

    @PostMapping("/createBranch")
    @Operation(summary = "Create a new branch from a snapshot", security = @SecurityRequirement(name = "bearerAuth"))
    public SnapshotResponse createBranch(
            @Valid @RequestBody CreateBranchRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.createBranch(currentUserId(authentication), authorizationHeader, request);
    }

    @PostMapping("/tag")
    @Operation(summary = "Assign a tag to an existing snapshot", security = @SecurityRequirement(name = "bearerAuth"))
    public SnapshotResponse tagSnapshot(
            @Valid @RequestBody TagSnapshotRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.tagSnapshot(authorizationHeader, request);
    }

    @GetMapping("/history/{fileId}")
    @Operation(summary = "Get file snapshot history sorted latest first", security = @SecurityRequirement(name = "bearerAuth"))
    public List<SnapshotResponse> getFileHistory(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return versionService.getFileHistory(fileId, authorizationHeader);
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
