package com.codesync.project;

import com.codesync.project.entity.Project;
import com.codesync.project.entity.ProjectMember;
import com.codesync.project.entity.Visibility;
import com.codesync.project.repository.ProjectMemberRepository;
import com.codesync.project.repository.ProjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectMembershipAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @AfterEach
    void tearDown() {
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
    }

    @Test
    void privateProjectIsReadableByMember() throws Exception {
        Project project = projectRepository.save(Project.builder()
                .ownerId(10L)
                .name("Private Workspace")
                .description("Private collaboration project")
                .language("Java")
                .visibility(Visibility.PRIVATE)
                .templateId("blank")
                .isArchived(false)
                .starCount(0)
                .forkCount(0)
                .build());

        projectMemberRepository.save(ProjectMember.builder()
                .projectId(project.getProjectId())
                .userId(22L)
                .build());

        mockMvc.perform(get("/projects/{id}", project.getProjectId())
                        .with(SecurityMockMvcRequestPostProcessors.user("22")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project.getProjectId()))
                .andExpect(jsonPath("$.ownerId").value(10L));
    }

    @Test
    void memberProjectsEndpointReturnsAssignedProjects() throws Exception {
        Project project = projectRepository.save(Project.builder()
                .ownerId(10L)
                .name("Shared Workspace")
                .description("Shared collaboration project")
                .language("Java")
                .visibility(Visibility.PRIVATE)
                .templateId("blank")
                .isArchived(false)
                .starCount(0)
                .forkCount(0)
                .build());

        projectMemberRepository.save(ProjectMember.builder()
                .projectId(project.getProjectId())
                .userId(22L)
                .build());

        mockMvc.perform(get("/projects/member/{userId}", 22L)
                        .with(SecurityMockMvcRequestPostProcessors.user("22")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(project.getProjectId()))
                .andExpect(jsonPath("$[0].name").value("Shared Workspace"));
    }
}
