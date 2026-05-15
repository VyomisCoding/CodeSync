package com.codesync.file.service;

import com.codesync.file.client.ProjectAccessClient;
import com.codesync.file.client.ProjectSummaryResponse;
import com.codesync.file.dto.*;
import com.codesync.file.entity.CodeFile;
import com.codesync.file.exception.ResourceNotFoundException;
import com.codesync.file.exception.UnauthorizedActionException;
import com.codesync.file.repository.CodeFileRepository;
import com.codesync.file.util.PathUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Transactional
public class FileServiceImpl implements FileService {

    private static final String TYPE_FILE = "FILE";
    private static final String TYPE_FOLDER = "FOLDER";

    private final CodeFileRepository repository;
    private final ProjectAccessClient projectAccessClient;

    @Override
    public CodeFile createFile(CreateFileRequest req, Long actorUserId, String authorizationHeader) {
        assertWritableProjectMember(req.getProjectId(), actorUserId, authorizationHeader);
        ensureParentFolderExists(req.getProjectId(), req.getParentPath());

        String path = PathUtil.build(req.getParentPath(), req.getName());
        ensurePathAvailable(req.getProjectId(), path, null, "File already exists");

        CodeFile file = CodeFile.builder()
                .projectId(req.getProjectId())
                .name(fileName(path))
                .path(path)
                .type(TYPE_FILE)
                .language(req.getLanguage())
                .content("")
                .size(0L)
                .createdById(actorUserId)
                .lastEditedBy(actorUserId)
                .isDeleted(false)
                .build();

        return repository.save(file);
    }

    @Override
    public CodeFile createFolder(CreateFolderRequest req, Long actorUserId, String authorizationHeader) {
        assertWritableProjectMember(req.getProjectId(), actorUserId, authorizationHeader);
        ensureParentFolderExists(req.getProjectId(), req.getParentPath());

        String path = PathUtil.build(req.getParentPath(), req.getFolderName());
        ensurePathAvailable(req.getProjectId(), path, null, "Folder already exists");

        CodeFile folder = CodeFile.builder()
                .projectId(req.getProjectId())
                .name(fileName(path))
                .path(path)
                .type(TYPE_FOLDER)
                .content("")
                .size(0L)
                .createdById(actorUserId)
                .lastEditedBy(actorUserId)
                .isDeleted(false)
                .build();

        return repository.save(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public CodeFile getById(Long fileId, String authorizationHeader) {
        CodeFile file = getActiveById(fileId);
        assertReadableProject(file.getProjectId(), authorizationHeader);
        return file;
    }

    @Override
    @Transactional(readOnly = true)
    public String getContent(Long fileId, String authorizationHeader) {
        CodeFile file = getActiveById(fileId);
        assertReadableProject(file.getProjectId(), authorizationHeader);
        ensureFile(file);
        return file.getContent();
    }

    @Override
    public CodeFile updateContent(Long fileId, UpdateContentRequest req, Long actorUserId, String authorizationHeader) {
        CodeFile file = getActiveById(fileId);
        assertWritableProjectMember(file.getProjectId(), actorUserId, authorizationHeader);
        ensureFile(file);

        String content = req.getContent();
        file.setContent(content);
        file.setSize((long) content.getBytes(StandardCharsets.UTF_8).length);
        file.setLastEditedBy(actorUserId);

        return repository.save(file);
    }

    @Override
    public CodeFile rename(Long fileId, RenameRequest req, Long actorUserId, String authorizationHeader) {
        CodeFile file = getActiveById(fileId);
        assertWritableProjectMember(file.getProjectId(), actorUserId, authorizationHeader);

        String oldPath = file.getPath();
        String newPath = PathUtil.build(PathUtil.parentPath(oldPath), req.getNewName());

        if (!oldPath.equals(newPath)) {
            ensurePathAvailable(file.getProjectId(), newPath, file.getFileId(), "A file or folder already exists at the new path");
        }

        file.setName(fileName(newPath));
        file.setPath(newPath);
        file.setLastEditedBy(actorUserId);

        if (isFolder(file)) {
            updateChildPaths(file.getProjectId(), oldPath, newPath, false);
        }

        return repository.save(file);
    }

    @Override
    public CodeFile move(Long fileId, MoveRequest req, Long actorUserId, String authorizationHeader) {
        CodeFile file = getActiveById(fileId);
        assertWritableProjectMember(file.getProjectId(), actorUserId, authorizationHeader);
        ensureParentFolderExists(file.getProjectId(), req.getNewParentPath());

        String oldPath = file.getPath();
        String newPath = PathUtil.build(req.getNewParentPath(), file.getName());

        if (isFolder(file) && (newPath.equals(oldPath) || newPath.startsWith(oldPath + "/"))) {
            throw new IllegalArgumentException("Folder cannot be moved inside itself");
        }

        if (!oldPath.equals(newPath)) {
            ensurePathAvailable(file.getProjectId(), newPath, file.getFileId(), "A file or folder already exists at the destination");
        }

        file.setPath(newPath);
        file.setLastEditedBy(actorUserId);

        if (isFolder(file)) {
            updateChildPaths(file.getProjectId(), oldPath, newPath, false);
        }

        return repository.save(file);
    }

    @Override
    public String delete(Long fileId, Long actorUserId, String authorizationHeader) {
        CodeFile file = getActiveById(fileId);
        assertWritableProjectMember(file.getProjectId(), actorUserId, authorizationHeader);
        file.setIsDeleted(true);
        file.setLastEditedBy(actorUserId);
        repository.save(file);

        if (isFolder(file)) {
            List<CodeFile> children = repository.findByProjectIdAndPathStartingWithAndIsDeletedFalse(
                    file.getProjectId(),
                    file.getPath() + "/"
            );
            children.forEach(child -> {
                child.setIsDeleted(true);
                child.setLastEditedBy(actorUserId);
            });
            repository.saveAll(children);
        }

        return "Deleted Successfully";
    }

    @Override
    public String restore(Long fileId, Long actorUserId, String authorizationHeader) {
        CodeFile file = getExistingById(fileId);
        assertWritableProjectMember(file.getProjectId(), actorUserId, authorizationHeader);
        ensureParentFolderExists(file.getProjectId(), PathUtil.parentPath(file.getPath()));

        ensurePathAvailable(file.getProjectId(), file.getPath(), file.getFileId(), "A file or folder already exists at this path");
        file.setIsDeleted(false);
        file.setLastEditedBy(actorUserId);
        repository.save(file);

        if (isFolder(file)) {
            List<CodeFile> children = repository.findByProjectIdAndPathStartingWithAndIsDeletedTrue(
                    file.getProjectId(),
                    file.getPath() + "/"
            );
            children.forEach(child -> {
                ensurePathAvailable(child.getProjectId(), child.getPath(), child.getFileId(), "A child path already exists");
                child.setIsDeleted(false);
                child.setLastEditedBy(actorUserId);
            });
            repository.saveAll(children);
        }

        return "Restored Successfully";
    }

    @Override
    @Transactional(readOnly = true)
    public List<CodeFile> listByProject(Long projectId, String authorizationHeader) {
        assertReadableProject(projectId, authorizationHeader);
        return repository.findByProjectIdAndIsDeletedFalse(projectId)
                .stream()
                .sorted(Comparator.comparing(CodeFile::getPath))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CodeFile> search(Long projectId, String keyword, String authorizationHeader) {
        assertReadableProject(projectId, authorizationHeader);
        String cleanKeyword = keyword == null ? "" : keyword.trim();
        String normalizedKeyword = cleanKeyword.toLowerCase(Locale.ROOT);
        Map<Long, CodeFile> results = new LinkedHashMap<>();

        repository.findByProjectIdAndNameContainingIgnoreCaseAndIsDeletedFalse(projectId, cleanKeyword)
                .forEach(file -> results.put(file.getFileId(), file));
        repository.findByProjectIdAndIsDeletedFalse(projectId).stream()
                .filter(this::isFile)
                .filter(file -> file.getContent() != null)
                .filter(file -> file.getContent().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .forEach(file -> results.put(file.getFileId(), file));

        return new ArrayList<>(results.values());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getTree(Long projectId, String authorizationHeader) {
        assertReadableProject(projectId, authorizationHeader);
        List<CodeFile> files = repository.findByProjectIdAndIsDeletedFalse(projectId)
                .stream()
                .sorted(Comparator.comparing(CodeFile::getPath))
                .toList();
        Map<String, Object> root = new LinkedHashMap<>();

        for (CodeFile file : files) {
            if (file.getPath().isBlank()) {
                continue;
            }

            String[] parts = file.getPath().split("/");
            Map<String, Object> current = root;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean last = (i == parts.length - 1);

                if (last) {
                    current.put(part, file.getType());
                } else {
                    Object next = current.computeIfAbsent(part, ignored -> new LinkedHashMap<String, Object>());
                    if (!(next instanceof Map)) {
                        next = new LinkedHashMap<String, Object>();
                        current.put(part, next);
                    }
                    current = (Map<String, Object>) next;
                }
            }
        }

        return root;
    }

    @Override
    public void copyProjectFiles(Long sourceProjectId, Long targetProjectId, Long actorUserId, String authorizationHeader) {
        assertReadableProject(sourceProjectId, authorizationHeader);
        ProjectSummaryResponse targetProject = assertWritableProjectMember(targetProjectId, actorUserId, authorizationHeader);

        if (!repository.findByProjectIdAndIsDeletedFalse(targetProject.getProjectId()).isEmpty()) {
            throw new IllegalArgumentException("Target project already contains files");
        }

        List<CodeFile> copies = repository.findByProjectIdAndIsDeletedFalse(sourceProjectId)
                .stream()
                .sorted(Comparator.comparing(CodeFile::getPath))
                .map(file -> CodeFile.builder()
                        .projectId(targetProjectId)
                        .name(file.getName())
                        .path(file.getPath())
                        .type(file.getType())
                        .language(file.getLanguage())
                        .content(file.getContent())
                        .size(file.getSize())
                        .createdById(actorUserId)
                        .lastEditedBy(actorUserId)
                        .isDeleted(false)
                        .build())
                .toList();

        repository.saveAll(copies);
    }

    private CodeFile getActiveById(Long fileId) {
        CodeFile file = getExistingById(fileId);
        if (Boolean.TRUE.equals(file.getIsDeleted())) {
            throw new ResourceNotFoundException("File not found");
        }
        return file;
    }

    private CodeFile getExistingById(Long fileId) {
        return repository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    private void assertReadableProject(Long projectId, String authorizationHeader) {
        projectAccessClient.getAccessibleProject(projectId, authorizationHeader);
    }

    private ProjectSummaryResponse assertWritableProjectMember(Long projectId, Long actorUserId, String authorizationHeader) {
        if (actorUserId == null) {
            throw new UnauthorizedActionException("Authentication required");
        }

        ProjectSummaryResponse project = projectAccessClient.getAccessibleProject(projectId, authorizationHeader);
        if (Boolean.TRUE.equals(project.getIsArchived())) {
            throw new IllegalArgumentException("Archived projects are read-only");
        }
        if (actorUserId.equals(project.getOwnerId())) {
            return project;
        }
        if (projectAccessClient.isProjectMember(projectId, actorUserId, authorizationHeader)) {
            return project;
        }

        throw new UnauthorizedActionException("Only the project owner or members can modify files");

    }

    private void ensurePathAvailable(Long projectId, String path, Long currentFileId, String message) {
        repository.findByProjectIdAndPathAndIsDeletedFalse(projectId, path)
                .filter(existing -> currentFileId == null || !existing.getFileId().equals(currentFileId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(message);
                });
    }

    private void ensureParentFolderExists(Long projectId, String parentPath) {
        String cleanParent = PathUtil.normalizeParent(parentPath);
        if (cleanParent.isBlank()) {
            return;
        }

        CodeFile parent = repository.findByProjectIdAndPathAndIsDeletedFalse(projectId, cleanParent)
                .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found"));

        if (!isFolder(parent)) {
            throw new IllegalArgumentException("Parent path must reference a folder");
        }
    }

    private void updateChildPaths(Long projectId, String oldPath, String newPath, boolean includeDeleted) {
        List<CodeFile> children = includeDeleted
                ? repository.findByProjectIdAndPathStartingWithAndIsDeletedTrue(projectId, oldPath + "/")
                : repository.findByProjectIdAndPathStartingWithAndIsDeletedFalse(projectId, oldPath + "/");

        children.forEach(child -> child.setPath(PathUtil.replacePrefix(child.getPath(), oldPath, newPath)));
        repository.saveAll(children);
    }

    private void ensureFile(CodeFile file) {
        if (!TYPE_FILE.equals(file.getType())) {
            throw new IllegalArgumentException("Operation is only valid for files");
        }
    }

    private boolean isFolder(CodeFile file) {
        return TYPE_FOLDER.equals(file.getType());
    }

    private boolean isFile(CodeFile file) {
        return TYPE_FILE.equals(file.getType());
    }

    private String fileName(String path) {
        int lastSlash = path.lastIndexOf("/");
        return lastSlash == -1 ? path : path.substring(lastSlash + 1);
    }
}
