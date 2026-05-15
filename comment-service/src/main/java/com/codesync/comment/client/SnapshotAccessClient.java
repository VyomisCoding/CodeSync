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
public class SnapshotAccessClient {

    private final RestTemplate restTemplate;

    public SnapshotAccessClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public SnapshotSummaryResponse getSnapshot(Long snapshotId, String authorizationHeader) {
        try {
            SnapshotSummaryResponse response = restTemplate.exchange(
                    "http://VERSION-SERVICE/versions/{snapshotId}",
                    HttpMethod.GET,
                    requestEntity(authorizationHeader),
                    SnapshotSummaryResponse.class,
                    snapshotId
            ).getBody();

            if (response == null) {
                throw new IllegalStateException("Version service returned an empty response");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Version service is unavailable", ex);
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
            return new ForbiddenException("Snapshot access denied");
        }
        if (status == 404) {
            return new ResourceNotFoundException("Snapshot not found");
        }
        return new IllegalStateException("Version service request failed", ex);
    }
}
