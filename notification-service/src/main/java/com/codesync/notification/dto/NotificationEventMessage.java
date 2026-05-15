package com.codesync.notification.dto;

public record NotificationEventMessage(
        String action,
        Long recipientId,
        Long notificationId,
        NotificationResponse notification
) {
}
