package com.codesync.project.service;

import com.codesync.project.dto.ProjectRequest;
import com.codesync.project.dto.ProjectResponse;

import java.util.List;

public interface ProjectService {

    ProjectResponse createProject(ProjectRequest request, Long ownerId);

    ProjectResponse getProjectById(Long projectId, Long requesterId);

    List<ProjectResponse> getProjectsByOwner(Long ownerId, Long requesterId);

    List<ProjectResponse> getProjectsByMember(Long userId, Long requesterId);

    List<ProjectResponse> getPublicProjects();

    List<ProjectResponse> searchProjects(String keyword);

    List<ProjectResponse> getProjectsByLanguage(String language);

    ProjectResponse updateProject(Long projectId, ProjectRequest request, Long ownerId);

    String archiveProject(Long projectId, Long ownerId);

    String deleteProject(Long projectId, Long ownerId);

    ProjectResponse forkProject(Long projectId, Long newOwnerId, String authorizationHeader);

    String starProject(Long projectId, Long requesterId);
}
