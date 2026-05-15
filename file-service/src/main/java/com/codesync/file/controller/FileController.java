package com.codesync.file.controller;

import com.codesync.file.dto.*;
import com.codesync.file.entity.CodeFile;
import com.codesync.file.service.FileService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "File Service", description = "Project file, folder, editor content, and tree APIs")
public class FileController {

    private final FileService service;

    @PostMapping
    @Operation(summary = "Create a file in a project", security = @SecurityRequirement(name = "bearerAuth"))
    public CodeFile createFile(
            @Valid @RequestBody CreateFileRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return service.createFile(request, authenticatedUserId(authentication), authorizationHeader);
    }

    @PostMapping("/folder")
    @Operation(summary = "Create a folder in a project", security = @SecurityRequirement(name = "bearerAuth"))
    public CodeFile createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return service.createFolder(request, authenticatedUserId(authentication), authorizationHeader);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get file or folder metadata by ID")
    public CodeFile getById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return service.getById(id, authorizationHeader);
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Get file content by ID")
    public String getContent(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return service.getContent(id, authorizationHeader);
    }

    @PutMapping("/{id}/content")
    @Operation(summary = "Update file content", security = @SecurityRequirement(name = "bearerAuth"))
    public CodeFile updateContent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateContentRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {

        return service.updateContent(id, request, authenticatedUserId(authentication), authorizationHeader);
    }

    @PutMapping("/{id}/rename")
    @Operation(summary = "Rename a file or folder", security = @SecurityRequirement(name = "bearerAuth"))
    public CodeFile rename(
            @PathVariable Long id,
            @Valid @RequestBody RenameRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {

        return service.rename(id, request, authenticatedUserId(authentication), authorizationHeader);
    }

    @PutMapping("/{id}/move")
    @Operation(summary = "Move a file or folder to another parent path", security = @SecurityRequirement(name = "bearerAuth"))
    public CodeFile move(
            @PathVariable Long id,
            @Valid @RequestBody MoveRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {

        return service.move(id, request, authenticatedUserId(authentication), authorizationHeader);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a file or folder", security = @SecurityRequirement(name = "bearerAuth"))
    public String delete(
            @PathVariable Long id,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return service.delete(id, authenticatedUserId(authentication), authorizationHeader);
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore a soft-deleted file or folder", security = @SecurityRequirement(name = "bearerAuth"))
    public String restore(
            @PathVariable Long id,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return service.restore(id, authenticatedUserId(authentication), authorizationHeader);
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "List active files and folders for a project")
    public List<CodeFile> list(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return service.listByProject(projectId, authorizationHeader);
    }

    @GetMapping("/project/{projectId}/search")
    @Operation(summary = "Search file names and file content inside a project")
    public List<CodeFile> search(
            @PathVariable Long projectId,
            @RequestParam String keyword,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return service.search(projectId, keyword, authorizationHeader);
    }

    @GetMapping("/project/{projectId}/tree")
    @Operation(summary = "Get the hierarchical file tree for a project")
    public Map<String, Object> tree(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return service.getTree(projectId, authorizationHeader);
    }

    @PostMapping("/internal/projects/{sourceProjectId}/copy")
    @Hidden
    public void copyProjectFiles(
            @PathVariable Long sourceProjectId,
            @Valid @RequestBody ProjectCopyRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        service.copyProjectFiles(
                sourceProjectId,
                request.getTargetProjectId(),
                authenticatedUserId(authentication),
                authorizationHeader
        );
    }

    private Long authenticatedUserId(Authentication authentication) {
        return authentication == null ? null : Long.valueOf(authentication.getName());
    }
}
