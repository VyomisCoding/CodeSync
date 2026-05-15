package com.codesync.project.service.impl;

import com.codesync.project.dto.ProjectRequest;
import com.codesync.project.dto.ProjectResponse;
import com.codesync.project.entity.Project;
import com.codesync.project.entity.Visibility;
import com.codesync.project.exception.ResourceNotFoundException;
import com.codesync.project.exception.UnauthorizedActionException;
import com.codesync.project.repository.ProjectRepository;
import com.codesync.project.service.ProjectMemberService;
import com.codesync.project.service.ProjectService;
import com.codesync.project.client.FileCopyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository repository;
    private final ProjectMemberService projectMemberService;
    private final FileCopyClient fileCopyClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional
    public ProjectResponse createProject(ProjectRequest request, Long ownerId) {

        Project project = Project.builder()
                .ownerId(ownerId)
                .name(request.getName())
                .description(request.getDescription())
                .language(request.getLanguage())
                .visibility(parseVisibility(request.getVisibility()))
                .templateId(request.getTemplateId())
                .isArchived(false)
                .starCount(0)
                .forkCount(0)
                .build();

        return map(repository.save(project));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long projectId, Long requesterId) {

        Project p = repository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!canReadProject(p, requesterId)) {
            throw new UnauthorizedActionException("Access denied");
        }

        return map(p);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjectsByOwner(Long ownerId, Long requesterId) {
        List<Project> projects = ownerId.equals(requesterId)
                ? repository.findByOwnerIdAndIsArchivedFalse(ownerId)
                : repository.findByOwnerIdAndVisibilityAndIsArchivedFalse(ownerId, Visibility.PUBLIC);

        return projects
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjectsByMember(Long userId, Long requesterId) {
        if (requesterId == null) {
            throw new UnauthorizedActionException("Authentication required");
        }
        if (!requesterId.equals(userId)) {
            throw new UnauthorizedActionException("You can only view your own memberships");
        }

        List<Long> projectIds = projectMemberService.getProjectIdsForMember(userId);
        if (projectIds.isEmpty()) {
            return List.of();
        }

        return repository.findByProjectIdInAndIsArchivedFalseOrderByUpdatedAtDesc(projectIds)
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> getPublicProjects() {
        return repository.findByVisibilityAndIsArchivedFalse(Visibility.PUBLIC)
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> searchProjects(String keyword) {
        return repository.searchPublicByName(keyword, Visibility.PUBLIC)
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjectsByLanguage(String language) {
        return repository.findByLanguageIgnoreCaseAndVisibilityAndIsArchivedFalse(language, Visibility.PUBLIC)
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(Long projectId,
                                         ProjectRequest request,
                                         Long ownerId) {

        Project p = repository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!p.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedActionException("Only owner can update");
        }

        p.setName(request.getName());
        p.setDescription(request.getDescription());
        p.setLanguage(request.getLanguage());
        p.setTemplateId(request.getTemplateId());
        p.setVisibility(parseVisibility(request.getVisibility()));

        return map(repository.save(p));
    }

    @Override
    @Transactional
    public String archiveProject(Long projectId, Long ownerId) {

        Project p = repository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!p.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedActionException("Only owner can archive");
        }

        p.setIsArchived(true);
        repository.save(p);

        return "Project archived successfully";
    }

    @Override
    @Transactional
    public String deleteProject(Long projectId, Long ownerId) {

        Project p = repository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!p.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedActionException("Only owner can delete");
        }

        projectMemberService.removeMembersByProjectId(projectId);
        repository.delete(p);

        return "Project deleted successfully";
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProjectResponse forkProject(Long projectId, Long newOwnerId, String authorizationHeader) {

        Project old = repository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!canReadProject(old, newOwnerId)) {
            throw new UnauthorizedActionException("Access denied");
        }

        Project forked = Project.builder()
                .ownerId(newOwnerId)
                .name(old.getName())
                .description(old.getDescription())
                .language(old.getLanguage())
                .visibility(old.getVisibility())
                .templateId(old.getTemplateId())
                .isArchived(false)
                .starCount(0)
                .forkCount(0)
                .build();

        Project savedFork = transactionTemplate.execute(status -> {
            old.setForkCount(old.getForkCount() + 1);
            repository.save(old);
            return repository.save(forked);
        });

        if (savedFork == null) {
            throw new IllegalStateException("Failed to create forked project");
        }

        try {
            fileCopyClient.copyProjectFiles(old.getProjectId(), savedFork.getProjectId(), authorizationHeader);
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status -> {
                repository.findByProjectId(savedFork.getProjectId()).ifPresent(repository::delete);
                repository.findByProjectId(old.getProjectId()).ifPresent(source -> {
                    source.setForkCount(Math.max(0, source.getForkCount() - 1));
                    repository.save(source);
                });
            });
            throw ex;
        }

        return map(savedFork);
    }

    @Override
    @Transactional
    public String starProject(Long projectId, Long requesterId) {

        Project p = repository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!canReadProject(p, requesterId)) {
            throw new UnauthorizedActionException("Access denied");
        }

        p.setStarCount(p.getStarCount() + 1);
        repository.save(p);

        return "Project starred successfully";
    }

    private ProjectResponse map(Project p) {
        return ProjectResponse.builder()
                .projectId(p.getProjectId())
                .ownerId(p.getOwnerId())
                .name(p.getName())
                .description(p.getDescription())
                .language(p.getLanguage())
                .visibility(p.getVisibility().name())
                .templateId(p.getTemplateId())
                .isArchived(p.getIsArchived())
                .starCount(p.getStarCount())
                .forkCount(p.getForkCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private Visibility parseVisibility(String value) {
        try {
            return Visibility.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Visibility must be PUBLIC or PRIVATE");
        }
    }

    private boolean canReadProject(Project project, Long requesterId) {
        if (project.getVisibility() == Visibility.PUBLIC) {
            return true;
        }
        if (requesterId == null) {
            return false;
        }
        if (project.getOwnerId().equals(requesterId)) {
            return true;
        }
        return projectMemberService.isMember(project.getProjectId(), requesterId);
    }
}
