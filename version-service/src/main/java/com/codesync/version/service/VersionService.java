package com.codesync.version.service;

import com.codesync.version.dto.CreateBranchRequest;
import com.codesync.version.dto.CreateSnapshotRequest;
import com.codesync.version.dto.SnapshotDiffResponse;
import com.codesync.version.dto.SnapshotResponse;
import com.codesync.version.dto.TagSnapshotRequest;

import java.util.List;

public interface VersionService {

    SnapshotResponse createSnapshot(Long requesterUserId, String authorizationHeader, CreateSnapshotRequest request);

    SnapshotResponse getSnapshotById(Long snapshotId, String authorizationHeader);

    List<SnapshotResponse> getSnapshotsByFile(Long fileId, String authorizationHeader);

    List<SnapshotResponse> getSnapshotsByProject(Long projectId, String authorizationHeader);

    List<SnapshotResponse> getSnapshotsByBranch(String branch, String authorizationHeader);

    SnapshotResponse getLatestSnapshot(Long fileId, String authorizationHeader);

    SnapshotResponse restoreSnapshot(Long snapshotId, Long requesterUserId, String authorizationHeader);

    SnapshotDiffResponse diffSnapshots(Long snapshotId1, Long snapshotId2, String authorizationHeader);

    SnapshotResponse createBranch(Long requesterUserId, String authorizationHeader, CreateBranchRequest request);

    SnapshotResponse tagSnapshot(String authorizationHeader, TagSnapshotRequest request);

    List<SnapshotResponse> getFileHistory(Long fileId, String authorizationHeader);
}
