package com.codesync.collab.controller;

import com.codesync.collab.dto.CodeChangeMessage;
import com.codesync.collab.dto.CursorUpdatePayload;
import com.codesync.collab.dto.CursorUpdateRequest;
import com.codesync.collab.exception.ForbiddenException;
import com.codesync.collab.service.CollabService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class CollabSocketController {

    private final CollabService collabService;

    public CollabSocketController(CollabService collabService) {
        this.collabService = collabService;
    }

    @MessageMapping("/session/{sessionId}/change")
    public void broadcastChange(
            @DestinationVariable UUID sessionId,
            @Valid CodeChangeMessage message,
            Principal principal) {
        collabService.broadcastChange(currentUserId(principal), sessionId, message);
    }

    @MessageMapping("/session/{sessionId}/cursor")
    public void updateCursor(
            @DestinationVariable UUID sessionId,
            @Valid CursorUpdatePayload request,
            Principal principal) {
        collabService.updateCursor(
                currentUserId(principal),
                new CursorUpdateRequest(sessionId, request.cursorLine(), request.cursorCol())
        );
    }

    private Long currentUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ForbiddenException("A valid STOMP Authorization header is required");
        }
        return Long.valueOf(principal.getName());
    }
}
