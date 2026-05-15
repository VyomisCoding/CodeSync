package com.codesync.version.service;

import com.codesync.version.client.FileAccessClient;
import com.codesync.version.client.FileSummaryResponse;
import com.codesync.version.client.ProjectAccessClient;
import com.codesync.version.dto.CreateBranchRequest;
import com.codesync.version.dto.CreateSnapshotRequest;
import com.codesync.version.dto.SnapshotDiffResponse;
import com.codesync.version.dto.SnapshotResponse;
import com.codesync.version.dto.TagSnapshotRequest;
import com.codesync.version.entity.Snapshot;
import com.codesync.version.exception.BadRequestException;
import com.codesync.version.exception.ConflictException;
import com.codesync.version.exception.ResourceNotFoundException;
import com.codesync.version.repository.SnapshotRepository;
import com.codesync.version.util.LineDiffUtil;
import com.codesync.version.util.Sha256Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VersionServiceImpl implements VersionService {

    private final SnapshotRepository snapshotRepository;
    private final ProjectAccessClient projectAccessClient;
    private final FileAccessClient fileAccessClient;
    private final boolean remoteValidationEnabled;

    public VersionServiceImpl(
            SnapshotRepository snapshotRepository,
            ProjectAccessClient projectAccessClient,
            FileAccessClient fileAccessClient,
            @Value("${codesync.version.remote-validation.enabled:true}") boolean remoteValidationEnabled) {
        this.snapshotRepository = snapshotRepository;
        this.projectAccessClient = projectAccessClient;
        this.fileAccessClient = fileAccessClient;
        this.remoteValidationEnabled = remoteValidationEnabled;
    }

    @Override
    @Transactional
    public SnapshotResponse createSnapshot(Long requesterUserId, String authorizationHeader, CreateSnapshotRequest request) {
        validateFileAccess(request.projectId(), request.fileId(), authorizationHeader);

        String branch = normalizeBranch(request.branch());
        String content = resolveContent(request.fileId(), authorizationHeader, request.content());
        Long parentSnapshotId = snapshotRepository.findTopByFileIdAndBranchOrderByCreatedAtDesc(request.fileId(), branch)
                .map(Snapshot::getSnapshotId)
                .orElse(null);

        Snapshot snapshot = new Snapshot();
        snapshot.setProjectId(request.projectId());
        snapshot.setFileId(request.fileId());
        snapshot.setAuthorId(requesterUserId);
        snapshot.setMessage(request.message());
        snapshot.setContent(content);
        snapshot.setHash(Sha256Util.hash(content));
        snapshot.setParentSnapshotId(parentSnapshotId);
        snapshot.setBranch(branch);

        return SnapshotResponse.fromEntity(snapshotRepository.save(snapshot));
    }

    @Override
    @Transactional(readOnly = true)
    public SnapshotResponse getSnapshotById(Long snapshotId, String authorizationHeader) {
        Snapshot snapshot = getSnapshotEntity(snapshotId);
        validateFileAccess(snapshot.getProjectId(), snapshot.getFileId(), authorizationHeader);
        return SnapshotResponse.fromEntity(snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SnapshotResponse> getSnapshotsByFile(Long fileId, String authorizationHeader) {
        validateFileAccess(null, fileId, authorizationHeader);
        return snapshotRepository.findByFileIdOrderByCreatedAtDesc(fileId)
                .stream()
                .map(SnapshotResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SnapshotResponse> getSnapshotsByProject(Long projectId, String authorizationHeader) {
        validateProjectAccess(projectId, authorizationHeader);
        return snapshotRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(SnapshotResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SnapshotResponse> getSnapshotsByBranch(String branch, String authorizationHeader) {
        return snapshotRepository.findByBranchOrderByCreatedAtDesc(branch)
                .stream()
                .filter(snapshot -> hasAccess(snapshot.getProjectId(), snapshot.getFileId(), authorizationHeader))
                .map(SnapshotResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SnapshotResponse getLatestSnapshot(Long fileId, String authorizationHeader) {
        validateFileAccess(null, fileId, authorizationHeader);
        Snapshot snapshot = snapshotRepository.findLatestByFileId(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("No snapshots found for file " + fileId));
        return SnapshotResponse.fromEntity(snapshot);
    }

    @Override
    @Transactional
    public SnapshotResponse restoreSnapshot(Long snapshotId, Long requesterUserId, String authorizationHeader) {
        Snapshot source = getSnapshotEntity(snapshotId);
        validateFileAccess(source.getProjectId(), source.getFileId(), authorizationHeader);

        if (remoteValidationEnabled) {
            fileAccessClient.updateFileContent(source.getFileId(), source.getContent(), authorizationHeader);
        }

        CreateSnapshotRequest restoreRequest = new CreateSnapshotRequest(
                source.getProjectId(),
                source.getFileId(),
                "Restored from snapshot #" + snapshotId,
                source.getContent(),
                source.getBranch()
        );
        return createSnapshot(requesterUserId, authorizationHeader, restoreRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public SnapshotDiffResponse diffSnapshots(Long snapshotId1, Long snapshotId2, String authorizationHeader) {
        Snapshot left = getSnapshotEntity(snapshotId1);
        Snapshot right = getSnapshotEntity(snapshotId2);

        validateFileAccess(left.getProjectId(), left.getFileId(), authorizationHeader);
        validateFileAccess(right.getProjectId(), right.getFileId(), authorizationHeader);

        if (!left.getFileId().equals(right.getFileId())) {
            throw new BadRequestException("Diff can only be generated for snapshots of the same file");
        }

        return new SnapshotDiffResponse(
                snapshotId1,
                snapshotId2,
                LineDiffUtil.diff(snapshotId1, left.getContent(), snapshotId2, right.getContent())
        );
    }

    @Override
    @Transactional
    public SnapshotResponse createBranch(Long requesterUserId, String authorizationHeader, CreateBranchRequest request) {
        Snapshot source = getSnapshotEntity(request.snapshotId());
        validateFileAccess(source.getProjectId(), source.getFileId(), authorizationHeader);

        String branch = normalizeBranch(request.branch());
        Long parentSnapshotId = source.getSnapshotId();

        Snapshot snapshot = new Snapshot();
        snapshot.setProjectId(source.getProjectId());
        snapshot.setFileId(source.getFileId());
        snapshot.setAuthorId(requesterUserId);
        snapshot.setMessage(defaultBranchMessage(request, source.getSnapshotId()));
        snapshot.setContent(source.getContent());
        snapshot.setHash(Sha256Util.hash(source.getContent()));
        snapshot.setParentSnapshotId(parentSnapshotId);
        snapshot.setBranch(branch);

        return SnapshotResponse.fromEntity(snapshotRepository.save(snapshot));
    }

    @Override
    @Transactional
    public SnapshotResponse tagSnapshot(String authorizationHeader, TagSnapshotRequest request) {
        Snapshot snapshot = getSnapshotEntity(request.snapshotId());
        validateFileAccess(snapshot.getProjectId(), snapshot.getFileId(), authorizationHeader);

        snapshotRepository.findByTag(request.tag())
                .filter(existing -> !existing.getSnapshotId().equals(snapshot.getSnapshotId()))
                .ifPresent(existing -> {
                    throw new ConflictException("Tag already exists: " + request.tag());
                });

        snapshot.setTag(request.tag());
        return SnapshotResponse.fromEntity(snapshotRepository.save(snapshot));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SnapshotResponse> getFileHistory(Long fileId, String authorizationHeader) {
        return getSnapshotsByFile(fileId, authorizationHeader);
    }

    private Snapshot getSnapshotEntity(Long snapshotId) {
        return snapshotRepository.findBySnapshotId(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("Snapshot not found: " + snapshotId));
    }

    private void validateProjectAccess(Long projectId, String authorizationHeader) {
        if (!remoteValidationEnabled || projectId == null) {
            return;
        }
        projectAccessClient.getAccessibleProject(projectId, authorizationHeader);
    }

    private void validateFileAccess(Long projectId, Long fileId, String authorizationHeader) {
        if (!remoteValidationEnabled || fileId == null) {
            return;
        }

        if (projectId != null) {
            projectAccessClient.getAccessibleProject(projectId, authorizationHeader);
        }

        FileSummaryResponse file = fileAccessClient.getReadableFile(fileId, authorizationHeader);
        if (projectId != null && !projectId.equals(file.projectId())) {
            throw new BadRequestException("File does not belong to the provided project");
        }
        if (!"FILE".equalsIgnoreCase(file.type())) {
            throw new BadRequestException("Snapshots can only be created for files");
        }
    }

    private boolean hasAccess(Long projectId, Long fileId, String authorizationHeader) {
        try {
            validateFileAccess(projectId, fileId, authorizationHeader);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String resolveContent(Long fileId, String authorizationHeader, String requestedContent) {
        if (requestedContent != null) {
            return requestedContent;
        }
        if (remoteValidationEnabled) {
            return fileAccessClient.getFileContent(fileId, authorizationHeader);
        }
        throw new BadRequestException("Content is required when remote file validation is disabled");
    }

    private String normalizeBranch(String branch) {
        return branch == null || branch.isBlank() ? "main" : branch.trim();
    }

    private String defaultBranchMessage(CreateBranchRequest request, Long sourceSnapshotId) {
        if (request.message() != null && !request.message().isBlank()) {
            return request.message().trim();
        }
        return "Branch " + request.branch().trim() + " created from snapshot #" + sourceSnapshotId;
    }
}
