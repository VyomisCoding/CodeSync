package com.codesync.notification.dto;

import com.codesync.notification.entity.Notification;
import com.codesync.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        Long recipientId,
        Long actorId,
        NotificationType type,
        String title,
        String message,
        Long relatedId,
        String relatedType,
        Boolean isRead,
        LocalDateTime createdAt
) {

    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getRecipientId(),
                notification.getActorId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getRelatedId(),
                notification.getRelatedType(),
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }
}
