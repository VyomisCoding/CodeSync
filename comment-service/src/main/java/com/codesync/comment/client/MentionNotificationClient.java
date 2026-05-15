package com.codesync.comment.client;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class MentionNotificationClient {

    private final RestTemplate restTemplate;

    public MentionNotificationClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendMentionNotification(Map<String, Object> payload, String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        restTemplate.exchange(
                "http://NOTIFICATION-SERVICE/notifications/mention",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Void.class
        );
    }
}
