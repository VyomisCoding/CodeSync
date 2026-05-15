package com.codesync.project.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class FileCopyClient {

    private final RestTemplate restTemplate;

    public void copyProjectFiles(Long sourceProjectId, Long targetProjectId, String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }

        HttpEntity<ProjectFileCopyRequest> requestEntity =
                new HttpEntity<>(new ProjectFileCopyRequest(targetProjectId), headers);

        try {
            restTemplate.exchange(
                    "http://FILE-SERVICE/files/internal/projects/{sourceProjectId}/copy",
                    HttpMethod.POST,
                    requestEntity,
                    Void.class,
                    sourceProjectId
            );
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to copy forked project files", ex);
        }
    }
}
