package com.codesync.file.client;

import com.codesync.file.exception.ResourceNotFoundException;
import com.codesync.file.exception.UnauthorizedActionException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ProjectAccessClient {

    private final RestTemplate restTemplate;

    public ProjectSummaryResponse getAccessibleProject(Long projectId, String authorizationHeader) {
        try {
            ProjectSummaryResponse response = restTemplate.exchange(
                    "http://PROJECT-SERVICE/projects/{projectId}",
                    HttpMethod.GET,
                    requestEntity(authorizationHeader),
                    ProjectSummaryResponse.class,
                    projectId
            ).getBody();

            if (response == null) {
                throw new IllegalStateException("Project service returned an empty response");
            }

            return response;
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Project service is unavailable", ex);
        }
    }

    public boolean isProjectMember(Long projectId, Long userId, String authorizationHeader) {
        try {
            List<ProjectSummaryResponse> projects = restTemplate.exchange(
                    "http://PROJECT-SERVICE/projects/member/{userId}",
                    HttpMethod.GET,
                    requestEntity(authorizationHeader),
                    new ParameterizedTypeReference<List<ProjectSummaryResponse>>() {
                    },
                    userId
            ).getBody();

            if (projects == null) {
                return false;
            }

            return projects.stream()
                    .map(ProjectSummaryResponse::getProjectId)
                    .anyMatch(id -> Objects.equals(id, projectId));
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Project service is unavailable", ex);
        }
    }

    private HttpEntity<Void> requestEntity(String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return new HttpEntity<>(headers);
    }

    private RuntimeException mapResponseException(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 401) {
            return new UnauthorizedActionException("Authentication required");
        }
        if (status == 403) {
            return new UnauthorizedActionException("Project access denied");
        }
        if (status == 404) {
            return new ResourceNotFoundException("Project not found");
        }
        return new IllegalStateException("Project service request failed", ex);
    }
}
