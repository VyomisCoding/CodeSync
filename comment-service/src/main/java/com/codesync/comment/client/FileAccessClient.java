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
public class FileAccessClient {

    private final RestTemplate restTemplate;

    public FileAccessClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public FileSummaryResponse getReadableFile(Long fileId, String authorizationHeader) {
        try {
            FileSummaryResponse response = restTemplate.exchange(
                    "http://FILE-SERVICE/files/{fileId}",
                    HttpMethod.GET,
                    requestEntity(authorizationHeader),
                    FileSummaryResponse.class,
                    fileId
            ).getBody();

            if (response == null) {
                throw new IllegalStateException("File service returned an empty response");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("File service is unavailable", ex);
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
            return new ForbiddenException("File access denied");
        }
        if (status == 404) {
            return new ResourceNotFoundException("File not found");
        }
        return new IllegalStateException("File service request failed", ex);
    }
}
