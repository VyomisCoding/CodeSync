package com.codesync.comment.client;

import com.codesync.comment.exception.ForbiddenException;
import com.codesync.comment.exception.ResourceNotFoundException;
import com.codesync.comment.exception.UnauthorizedException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
public class ProjectAccessClient {

    private final RestTemplate restTemplate;

    public ProjectAccessClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

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
            return new UnauthorizedException("Authentication required");
        }
        if (status == 403) {
            return new ForbiddenException("Project access denied");
        }
        if (status == 404) {
            return new ResourceNotFoundException("Project not found");
        }
        return new IllegalStateException("Project service request failed", ex);
    }
}
