package com.codesync.notification.controller;

import com.codesync.notification.dto.ActionResponse;
import com.codesync.notification.dto.BulkNotificationRequest;
import com.codesync.notification.dto.MentionNotificationRequest;
import com.codesync.notification.dto.NotificationResponse;
import com.codesync.notification.dto.SendNotificationRequest;
import com.codesync.notification.dto.UnreadCountResponse;
import com.codesync.notification.exception.ForbiddenException;
import com.codesync.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/notifications")
@Tag(name = "Notification Service", description = "In-app notifications, unread counters, bulk dispatch, and email delivery APIs")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a notification to one recipient", security = @SecurityRequirement(name = "bearerAuth"))
    public NotificationResponse send(
            @Valid @RequestBody SendNotificationRequest request,
            Authentication authentication) {
        return notificationService.send(request, currentUserId(authentication), isAdmin(authentication));
    }

    @PostMapping("/sendBulk")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Broadcast notifications to multiple recipients", security = @SecurityRequirement(name = "bearerAuth"))
    public List<NotificationResponse> sendBulk(
            @Valid @RequestBody BulkNotificationRequest request,
            Authentication authentication) {
        return notificationService.sendBulk(request, currentUserId(authentication), isAdmin(authentication));
    }

    @Hidden
    @PostMapping("/mention")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse sendMention(
            @Valid @RequestBody MentionNotificationRequest request,
            Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return notificationService.sendMention(
                request,
                currentUserId(authentication),
                isAdmin(authentication),
                authorizationHeader
        );
    }

    @GetMapping("/recipient/{recipientId}")
    @Operation(summary = "Get notifications for a recipient", security = @SecurityRequirement(name = "bearerAuth"))
    public List<NotificationResponse> getByRecipient(
            @PathVariable @Positive Long recipientId,
            Authentication authentication) {
        return notificationService.getByRecipient(recipientId, currentUserId(authentication), isAdmin(authentication));
    }

    @PutMapping("/read/{notificationId}")
    @Operation(summary = "Mark a notification as read", security = @SecurityRequirement(name = "bearerAuth"))
    public NotificationResponse markAsRead(
            @PathVariable @Positive Long notificationId,
            Authentication authentication) {
        return notificationService.markAsRead(notificationId, currentUserId(authentication), isAdmin(authentication));
    }

    @PutMapping("/readAll/{recipientId}")
    @Operation(summary = "Mark all notifications as read for a recipient", security = @SecurityRequirement(name = "bearerAuth"))
    public ActionResponse markAllRead(
            @PathVariable @Positive Long recipientId,
            Authentication authentication) {
        long affected = notificationService.markAllRead(recipientId, currentUserId(authentication), isAdmin(authentication));
        return new ActionResponse("Marked notifications as read", affected);
    }

    @DeleteMapping("/deleteRead/{recipientId}")
    @Operation(summary = "Delete all read notifications for a recipient", security = @SecurityRequirement(name = "bearerAuth"))
    public ActionResponse deleteRead(
            @PathVariable @Positive Long recipientId,
            Authentication authentication) {
        long affected = notificationService.deleteRead(recipientId, currentUserId(authentication), isAdmin(authentication));
        return new ActionResponse("Deleted read notifications", affected);
    }

    @GetMapping("/unreadCount/{recipientId}")
    @Operation(summary = "Get unread notification count for a recipient", security = @SecurityRequirement(name = "bearerAuth"))
    public UnreadCountResponse getUnreadCount(
            @PathVariable @Positive Long recipientId,
            Authentication authentication) {
        long unreadCount = notificationService.getUnreadCount(recipientId, currentUserId(authentication), isAdmin(authentication));
        return new UnreadCountResponse(recipientId, unreadCount);
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete a notification", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteNotification(
            @PathVariable @Positive Long notificationId,
            Authentication authentication) {
        notificationService.deleteNotification(notificationId, currentUserId(authentication), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/all")
    @Operation(summary = "Get all notifications", security = @SecurityRequirement(name = "bearerAuth"))
    public List<NotificationResponse> getAll(Authentication authentication) {
        ensureAdmin(authentication);
        return notificationService.getAll();
    }

    private Long currentUserId(Authentication authentication) {
        return authentication == null ? null : Long.valueOf(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private void ensureAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ForbiddenException("Only admins can access this resource");
        }
    }
}
