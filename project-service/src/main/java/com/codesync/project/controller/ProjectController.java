package com.codesync.project.controller;

import com.codesync.project.dto.ApiResponse;
import com.codesync.project.dto.ProjectRequest;
import com.codesync.project.dto.ProjectResponse;
import com.codesync.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Tag(name = "Project Service", description = "Project discovery, ownership, forking, and lifecycle APIs")
public class ProjectController {

    private final ProjectService service;

    @PostMapping
    @Operation(summary = "Create a new project", security = @SecurityRequirement(name = "bearerAuth"))
    public ProjectResponse create(
            @Valid @RequestBody ProjectRequest request,
            Authentication authentication) {

        return service.createProject(request, authenticatedUserId(authentication));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a project by ID")
    public ProjectResponse getById(
            @PathVariable Long id,
            Authentication authentication) {

        return service.getProjectById(id, authenticatedUserId(authentication));
    }

    @GetMapping("/owner/{ownerId}")
    @Operation(summary = "List projects by owner; public projects are visible to guests")
    public List<ProjectResponse> byOwner(
            @PathVariable Long ownerId,
            Authentication authentication) {
        return service.getProjectsByOwner(ownerId, authenticatedUserId(authentication));
    }

    @GetMapping("/my")
    @Operation(summary = "List projects owned by the current user", security = @SecurityRequirement(name = "bearerAuth"))
    public List<ProjectResponse> myProjects(Authentication authentication) {
        Long userId = authenticatedUserId(authentication);
        return service.getProjectsByOwner(userId, userId);
    }

    @GetMapping("/member/{userId}")
    @Operation(summary = "List projects where the current user is a member", security = @SecurityRequirement(name = "bearerAuth"))
    public List<ProjectResponse> memberProjects(
            @PathVariable Long userId,
            Authentication authentication) {
        return service.getProjectsByMember(userId, authenticatedUserId(authentication));
    }

    @GetMapping("/public")
    @Operation(summary = "Browse public projects")
    public List<ProjectResponse> publicProjects() {
        return service.getPublicProjects();
    }

    @GetMapping("/search")
    @Operation(summary = "Search public projects by name")
    public List<ProjectResponse> search(
            @RequestParam String keyword) {

        return service.searchProjects(keyword);
    }

    @GetMapping("/language/{language}")
    @Operation(summary = "Filter public projects by language")
    public List<ProjectResponse> byLanguage(
            @PathVariable String language) {

        return service.getProjectsByLanguage(language);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a project", security = @SecurityRequirement(name = "bearerAuth"))
    public ProjectResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request,
            Authentication authentication) {

        return service.updateProject(
                id,
                request,
                authenticatedUserId(authentication)
        );
    }

    @PutMapping("/{id}/archive")
    @Operation(summary = "Archive a project", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse archive(
            @PathVariable Long id,
            Authentication authentication) {

        return new ApiResponse(
                service.archiveProject(
                        id,
                        authenticatedUserId(authentication)
                )
        );
    }

    @PutMapping("/{id}/star")
    @Operation(summary = "Star a project", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse star(@PathVariable Long id,
                            Authentication authentication) {

        return new ApiResponse(
                service.starProject(id, authenticatedUserId(authentication))
        );
    }

    @PostMapping("/{id}/fork")
    @Operation(summary = "Fork a public project into the current account", security = @SecurityRequirement(name = "bearerAuth"))
    public ProjectResponse fork(
            @PathVariable Long id,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {

        return service.forkProject(
                id,
                authenticatedUserId(authentication),
                authorizationHeader
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse delete(
            @PathVariable Long id,
            Authentication authentication) {

        return new ApiResponse(
                service.deleteProject(
                        id,
                        authenticatedUserId(authentication)
                )
        );
    }

    private Long authenticatedUserId(Authentication authentication) {
        return authentication == null ? null : Long.valueOf(authentication.getName());
    }
}
