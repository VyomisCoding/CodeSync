package com.codesync.notification.dto;

import com.codesync.notification.entity.NotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SendNotificationRequest(
        @NotNull @Positive Long recipientId,
        @Positive Long actorId,
        @NotNull NotificationType type,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 2000) String message,
        @Positive Long relatedId,
        @Size(max = 100) String relatedType,
        @Email @Size(max = 255) String recipientEmail,
        Boolean sendEmail
) {
}
