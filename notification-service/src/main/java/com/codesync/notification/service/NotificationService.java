package com.codesync.notification.service;

import com.codesync.notification.dto.BulkNotificationRequest;
import com.codesync.notification.dto.MentionNotificationRequest;
import com.codesync.notification.dto.NotificationResponse;
import com.codesync.notification.dto.SendNotificationRequest;

import java.util.List;

public interface NotificationService {

    NotificationResponse send(SendNotificationRequest request, Long authenticatedUserId, boolean admin);

    List<NotificationResponse> sendBulk(BulkNotificationRequest request, Long authenticatedUserId, boolean admin);

    NotificationResponse markAsRead(Long notificationId, Long authenticatedUserId, boolean admin);

    long markAllRead(Long recipientId, Long authenticatedUserId, boolean admin);

    long deleteRead(Long recipientId, Long authenticatedUserId, boolean admin);

    List<NotificationResponse> getByRecipient(Long recipientId, Long authenticatedUserId, boolean admin);

    long getUnreadCount(Long recipientId, Long authenticatedUserId, boolean admin);

    void deleteNotification(Long notificationId, Long authenticatedUserId, boolean admin);

    void sendEmail(String recipientEmail, String title, String body);

    List<NotificationResponse> getAll();

    NotificationResponse sendMention(
            MentionNotificationRequest request,
            Long authenticatedUserId,
            boolean admin,
            String authorizationHeader
    );
}
