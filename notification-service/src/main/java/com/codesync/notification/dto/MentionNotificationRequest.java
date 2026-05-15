package com.codesync.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MentionNotificationRequest(
        @Positive Long actorId,
        @NotBlank @Size(max = 50) String mentionedUsername,
        @Positive Long relatedId,
        @Size(max = 100) String relatedType,
        @Positive Long projectId,
        @Positive Long fileId,
        @Size(max = 255) String title,
        @Size(max = 2000) String message,
        @Size(max = 500) String url,
        @Email @Size(max = 255) String recipientEmail,
        Boolean sendEmail
) {
}
