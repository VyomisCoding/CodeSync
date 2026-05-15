package com.codesync.notification.client;

import com.codesync.notification.exception.UnauthorizedException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class AuthDirectoryClient {

    private final RestTemplate restTemplate;

    public AuthDirectoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<AuthUserSummaryResponse> findByUsername(String username, String authorizationHeader) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString("http://AUTH-SERVICE/auth/search")
                    .queryParam("keyword", username)
                    .build(true)
                    .toUri();

            List<AuthUserSummaryResponse> results = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    requestEntity(authorizationHeader),
                    new ParameterizedTypeReference<List<AuthUserSummaryResponse>>() {
                    }
            ).getBody();

            if (results == null || results.isEmpty()) {
                return Optional.empty();
            }

            String normalizedUsername = username.toLowerCase(Locale.ROOT);
            return results.stream()
                    .filter(result -> result.username() != null
                            && result.username().toLowerCase(Locale.ROOT).equals(normalizedUsername))
                    .findFirst()
                    .or(() -> results.stream().findFirst());
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Auth directory lookup is unavailable", ex);
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
        return new IllegalStateException("Auth directory lookup failed", ex);
    }
}
