package com.codesync.notification.service;

import com.codesync.notification.client.AuthDirectoryClient;
import com.codesync.notification.client.AuthUserSummaryResponse;
import com.codesync.notification.dto.BulkNotificationRequest;
import com.codesync.notification.dto.MentionNotificationRequest;
import com.codesync.notification.dto.NotificationEventMessage;
import com.codesync.notification.dto.NotificationResponse;
import com.codesync.notification.dto.SendNotificationRequest;
import com.codesync.notification.dto.UnreadCountResponse;
import com.codesync.notification.entity.Notification;
import com.codesync.notification.entity.NotificationType;
import com.codesync.notification.exception.BadRequestException;
import com.codesync.notification.exception.ForbiddenException;
import com.codesync.notification.exception.ResourceNotFoundException;
import com.codesync.notification.exception.UnauthorizedException;
import com.codesync.notification.repository.NotificationRepository;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final Comparator<Notification> LATEST_FIRST =
            Comparator.comparing(Notification::getCreatedAt).reversed()
                    .thenComparing(Notification::getNotificationId, Comparator.reverseOrder());

    private final NotificationRepository notificationRepository;
    private final JavaMailSender javaMailSender;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuthDirectoryClient authDirectoryClient;
    private final String mailHost;
    private final String mailFrom;
    private final String mailFromName;
    private final int emailLogPreviewLength;
    private final boolean authLookupEnabled;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            JavaMailSender javaMailSender,
            SimpMessagingTemplate messagingTemplate,
            AuthDirectoryClient authDirectoryClient,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${codesync.notification.email.from}") String mailFrom,
            @Value("${codesync.notification.email.from-name:CodeSync Notifications}") String mailFromName,
            @Value("${codesync.notification.email.log-preview-length:120}") int emailLogPreviewLength,
            @Value("${codesync.notification.auth-lookup.enabled:true}") boolean authLookupEnabled) {
        this.notificationRepository = notificationRepository;
        this.javaMailSender = javaMailSender;
        this.messagingTemplate = messagingTemplate;
        this.authDirectoryClient = authDirectoryClient;
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
        this.mailFromName = mailFromName;
        this.emailLogPreviewLength = emailLogPreviewLength;
        this.authLookupEnabled = authLookupEnabled;
    }

    @Override
    @Transactional
    public NotificationResponse send(SendNotificationRequest request, Long authenticatedUserId, boolean admin) {
        ensureAuthenticated(authenticatedUserId);
        validateRelationship(request.relatedId(), request.relatedType());
        Long actorId = resolveActorId(request.actorId(), authenticatedUserId, admin);

        Notification saved = persistNotification(
                request.recipientId(),
                actorId,
                request.type(),
                request.title(),
                request.message(),
                request.relatedId(),
                request.relatedType()
        );

        if (Boolean.TRUE.equals(request.sendEmail()) && request.recipientEmail() != null && !request.recipientEmail().isBlank()) {
            sendEmail(request.recipientEmail(), saved.getTitle(), saved.getMessage());
        }

        publishCreated(saved);
        return NotificationResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public List<NotificationResponse> sendBulk(BulkNotificationRequest request, Long authenticatedUserId, boolean admin) {
        ensureAuthenticated(authenticatedUserId);
        if (!admin) {
            throw new ForbiddenException("Only admins can send bulk notifications");
        }
        validateRelationship(request.relatedId(), request.relatedType());

        Long actorId = resolveActorId(request.actorId(), authenticatedUserId, true);
        Set<Long> uniqueRecipientIds = new LinkedHashSet<>(request.recipientIds());
        List<NotificationResponse> responses = new ArrayList<>(uniqueRecipientIds.size());

        for (Long recipientId : uniqueRecipientIds) {
            Notification saved = persistNotification(
                    recipientId,
                    actorId,
                    request.type(),
                    request.title(),
                    request.message(),
                    request.relatedId(),
                    request.relatedType()
            );
            publishCreated(saved);
            responses.add(NotificationResponse.fromEntity(saved));
        }

        if (Boolean.TRUE.equals(request.sendEmail()) && request.recipientEmails() != null) {
            for (String emailAddress : new LinkedHashSet<>(request.recipientEmails())) {
                if (emailAddress != null && !emailAddress.isBlank()) {
                    sendEmail(emailAddress, request.title(), request.message());
                }
            }
        }

        return responses;
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long authenticatedUserId, boolean admin) {
        ensureAuthenticated(authenticatedUserId);
        Notification notification = getNotification(notificationId);
        ensureRecipientAccess(notification.getRecipientId(), authenticatedUserId, admin);

        notification.setIsRead(true);
        Notification saved = notificationRepository.save(notification);
        publishUpdated(saved);
        publishUnreadCount(saved.getRecipientId());
        return NotificationResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public long markAllRead(Long recipientId, Long authenticatedUserId, boolean admin) {
        ensureAuthenticated(authenticatedUserId);
        ensureRecipientAccess(recipientId, authenticatedUserId, admin);

        List<Notification> unreadNotifications = notificationRepository.findByRecipientIdAndIsRead(recipientId, false);
        unreadNotifications.forEach(notification -> notification.setIsRead(true));
        if (!unreadNotifications.isEmpty()) {
            notificationRepository.saveAll(unreadNotifications);
        }

        publishUnreadCount(recipientId);
        return unreadNotifications.size();
    }

    @Override
    @Transactional
    public long deleteRead(Long recipientId, Long authenticatedUserId, boolean admin) {
        ensureAuthenticated(authenticatedUserId);
        ensureRecipientAccess(recipientId, authenticatedUserId, admin);

        long deletedCount = notificationRepository.deleteByRecipientIdAndIsRead(recipientId, true);
        publishUnreadCount(recipientId);
        return deletedCount;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getByRecipient(Long recipientId, Long authenticatedUserId, boolean admin) {
        ensureAuthenticated(authenticatedUserId);
        ensureRecipientAccess(recipientId, authenticatedUserId, admin);

        return notificationRepository.findByRecipientId(recipientId).stream()
                .sorted(LATEST_FIRST)
                .map(NotificationResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long recipientId, Long authenticatedUserId, boolean admin) {
        ensureAuthenticated(authenticatedUserId);
        ensureRecipientAccess(recipientId, authenticatedUserId, admin);
        return notificationRepository.countByRecipientIdAndIsRead(recipientId, false);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId, Long authenticatedUserId, boolean admin) {
        ensureAuthenticated(authenticatedUserId);
        Notification notification = getNotification(notificationId);
        ensureRecipientAccess(notification.getRecipientId(), authenticatedUserId, admin);

        long deletedCount = notificationRepository.deleteByNotificationId(notificationId);
        if (deletedCount == 0) {
            throw new ResourceNotFoundException("Notification not found: " + notificationId);
        }

        publishDeleted(notification.getRecipientId(), notificationId);
        publishUnreadCount(notification.getRecipientId());
    }

    @Override
    public void sendEmail(String recipientEmail, String title, String body) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new BadRequestException("recipientEmail is required to send email");
        }

        if (mailHost == null || mailHost.isBlank()) {
            log.info(
                    "SMTP unavailable; logging email preview instead | recipient={} | title={} | preview={}",
                    recipientEmail,
                    title,
                    abbreviate(body)
            );
            return;
        }

        try {
            var mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper mailMessage = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            mailMessage.setTo(recipientEmail);
            mailMessage.setFrom(mailFrom, resolveMailFromName());
            mailMessage.setSubject(title);
            mailMessage.setText(body, false);
            javaMailSender.send(mimeMessage);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.warn(
                    "Email delivery failed; logging email preview instead | recipient={} | title={} | preview={}",
                    recipientEmail,
                    title,
                    abbreviate(body),
                    ex
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getAll() {
        return notificationRepository.findAll().stream()
                .sorted(LATEST_FIRST)
                .map(NotificationResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public NotificationResponse sendMention(
            MentionNotificationRequest request,
            Long authenticatedUserId,
            boolean admin,
            String authorizationHeader) {
        ensureAuthenticated(authenticatedUserId);
        if (!authLookupEnabled) {
            throw new IllegalStateException("Mention recipient lookup is disabled");
        }

        Long actorId = resolveActorId(request.actorId(), authenticatedUserId, admin);
        AuthUserSummaryResponse recipient = authDirectoryClient.findByUsername(request.mentionedUsername(), authorizationHeader)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mention recipient not found: " + request.mentionedUsername()
                ));

        Long relatedId = request.relatedId();
        String relatedType = normalizeRelatedType(request.relatedType(), "COMMENT");
        validateRelationship(relatedId, relatedType);

        String title = request.title();
        if (title == null || title.isBlank()) {
            title = "You were mentioned in CodeSync";
        }

        String message = request.message();
        if (message == null || message.isBlank()) {
            String scope = request.projectId() == null
                    ? "a comment"
                    : "a comment in project " + request.projectId();
            message = "User " + actorId + " mentioned you in " + scope + ".";
            if (request.fileId() != null) {
                message += " File " + request.fileId() + ".";
            }
        }

        Notification saved = persistNotification(
                recipient.userId(),
                actorId,
                NotificationType.MENTION,
                title,
                message,
                relatedId,
                relatedType
        );

        if (Boolean.TRUE.equals(request.sendEmail()) && request.recipientEmail() != null && !request.recipientEmail().isBlank()) {
            sendEmail(request.recipientEmail(), saved.getTitle(), saved.getMessage());
        }

        publishCreated(saved);
        return NotificationResponse.fromEntity(saved);
    }

    private Notification persistNotification(
            Long recipientId,
            Long actorId,
            NotificationType type,
            String title,
            String message,
            Long relatedId,
            String relatedType) {
        Notification notification = new Notification();
        notification.setRecipientId(requirePositive(recipientId, "recipientId"));
        notification.setActorId(actorId);
        notification.setType(Objects.requireNonNull(type, "type is required"));
        notification.setTitle(normalizeText(title, "title", 255));
        notification.setMessage(normalizeText(message, "message", 2000));
        notification.setRelatedId(relatedId);
        notification.setRelatedType(normalizeRelatedType(relatedType, null));
        notification.setIsRead(false);
        return notificationRepository.save(notification);
    }

    private Notification getNotification(Long notificationId) {
        return notificationRepository.findByNotificationId(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
    }

    private void ensureAuthenticated(Long authenticatedUserId) {
        if (authenticatedUserId == null) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    private void ensureRecipientAccess(Long recipientId, Long authenticatedUserId, boolean admin) {
        if (admin) {
            return;
        }
        if (!Objects.equals(recipientId, authenticatedUserId)) {
            throw new ForbiddenException("You may only access your own notifications");
        }
    }

    private Long resolveActorId(Long requestedActorId, Long authenticatedUserId, boolean admin) {
        if (requestedActorId == null) {
            return authenticatedUserId;
        }
        if (!admin && !Objects.equals(requestedActorId, authenticatedUserId)) {
            throw new ForbiddenException("Only admins may override actorId");
        }
        return requestedActorId;
    }

    private void validateRelationship(Long relatedId, String relatedType) {
        boolean hasRelatedId = relatedId != null;
        boolean hasRelatedType = relatedType != null && !relatedType.isBlank();
        if (hasRelatedId != hasRelatedType) {
            throw new BadRequestException("relatedId and relatedType must be supplied together");
        }
    }

    private String normalizeRelatedType(String relatedType, String defaultValue) {
        String value = relatedType;
        if ((value == null || value.isBlank()) && defaultValue != null) {
            value = defaultValue;
        }
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 100) {
            throw new BadRequestException("relatedType exceeds 100 characters");
        }
        return normalized;
    }

    private Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new BadRequestException(fieldName + " must be positive");
        }
        return value;
    }

    private String normalizeText(String value, String fieldName, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new BadRequestException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new BadRequestException(fieldName + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private void publishCreated(Notification notification) {
        Long recipientId = notification.getRecipientId();
        messagingTemplate.convertAndSend(
                "/topic/notifications/" + recipientId,
                new NotificationEventMessage(
                        "CREATED",
                        recipientId,
                        notification.getNotificationId(),
                        NotificationResponse.fromEntity(notification)
                )
        );
        publishUnreadCount(recipientId);
    }

    private void publishUpdated(Notification notification) {
        messagingTemplate.convertAndSend(
                "/topic/notifications/" + notification.getRecipientId(),
                new NotificationEventMessage(
                        "UPDATED",
                        notification.getRecipientId(),
                        notification.getNotificationId(),
                        NotificationResponse.fromEntity(notification)
                )
        );
    }

    private void publishDeleted(Long recipientId, Long notificationId) {
        messagingTemplate.convertAndSend(
                "/topic/notifications/" + recipientId,
                new NotificationEventMessage(
                        "DELETED",
                        recipientId,
                        notificationId,
                        null
                )
        );
    }

    private void publishUnreadCount(Long recipientId) {
        messagingTemplate.convertAndSend(
                "/topic/unread/" + recipientId,
                new UnreadCountResponse(
                        recipientId,
                        notificationRepository.countByRecipientIdAndIsRead(recipientId, false)
                )
        );
    }

    private String abbreviate(String body) {
        String sanitized = (body == null ? "" : body)
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
        if (sanitized.length() <= emailLogPreviewLength) {
            return sanitized;
        }
        return sanitized.substring(0, Math.max(0, emailLogPreviewLength)) + "...";
    }

    private String resolveMailFromName() {
        if (mailFromName == null || mailFromName.isBlank()) {
            return "CodeSync Notifications";
        }
        return mailFromName.trim();
    }
}
