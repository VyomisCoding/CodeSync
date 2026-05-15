package com.codesync.notification.repository;

import com.codesync.notification.entity.Notification;
import com.codesync.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByNotificationId(Long notificationId);

    List<Notification> findByRecipientId(Long recipientId);

    List<Notification> findByRecipientIdAndIsRead(Long recipientId, Boolean isRead);

    long countByRecipientIdAndIsRead(Long recipientId, Boolean isRead);

    List<Notification> findByType(NotificationType type);

    List<Notification> findByRelatedId(Long relatedId);

    long deleteByNotificationId(Long notificationId);

    long deleteByRecipientIdAndIsRead(Long recipientId, Boolean isRead);
}
