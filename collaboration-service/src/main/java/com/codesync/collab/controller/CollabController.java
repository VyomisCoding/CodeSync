package com.codesync.collab.controller;

import com.codesync.collab.dto.ApiResponse;
import com.codesync.collab.dto.CollabSessionRequest;
import com.codesync.collab.dto.CollabSessionResponse;
import com.codesync.collab.dto.CursorUpdateRequest;
import com.codesync.collab.dto.EndSessionRequest;
import com.codesync.collab.dto.JoinSessionRequest;
import com.codesync.collab.dto.KickParticipantRequest;
import com.codesync.collab.dto.LeaveSessionRequest;
import com.codesync.collab.dto.ParticipantResponse;
import com.codesync.collab.exception.ForbiddenException;
import com.codesync.collab.service.CollabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sessions")
@Tag(name = "Collaboration Service", description = "Collaboration session and participant APIs")
public class CollabController {

    private final CollabService collabService;

    public CollabController(CollabService collabService) {
        this.collabService = collabService;
    }

    @PostMapping("/create")
    @Operation(summary = "Create a collaboration session")
    public CollabSessionResponse createSession(
            @Valid @RequestBody CollabSessionRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return collabService.createSession(currentUserId(authentication), authorizationHeader, request);
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get a collaboration session by sessionId")
    public CollabSessionResponse getSession(
            @PathVariable UUID sessionId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return collabService.getSessionById(sessionId, authorizationHeader);
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get collaboration sessions for a project")
    public List<CollabSessionResponse> getSessionsByProject(
            @PathVariable Long projectId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return collabService.getSessionsByProject(projectId, authorizationHeader);
    }

    @PostMapping("/join")
    @Operation(summary = "Join a collaboration session")
    public ParticipantResponse joinSession(
            @Valid @RequestBody JoinSessionRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return collabService.joinSession(currentUserId(authentication), authorizationHeader, request);
    }

    @PostMapping("/leave")
    @Operation(summary = "Leave a collaboration session")
    public ApiResponse leaveSession(
            @Valid @RequestBody LeaveSessionRequest request,
            Authentication authentication) {
        collabService.leaveSession(currentUserId(authentication), request);
        return new ApiResponse("Left session successfully");
    }

    @PostMapping("/end")
    @Operation(summary = "End a collaboration session")
    public ApiResponse endSession(
            @Valid @RequestBody EndSessionRequest request,
            Authentication authentication) {
        collabService.endSession(currentUserId(authentication), request);
        return new ApiResponse("Session ended successfully");
    }

    @GetMapping("/participants/{sessionId}")
    @Operation(summary = "Get participants for a collaboration session")
    public List<ParticipantResponse> getParticipants(
            @PathVariable UUID sessionId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return collabService.getParticipants(sessionId, authorizationHeader);
    }

    @PutMapping("/cursor")
    @Operation(summary = "Update the current user's cursor position")
    public ParticipantResponse updateCursor(
            @Valid @RequestBody CursorUpdateRequest request,
            Authentication authentication) {
        return collabService.updateCursor(currentUserId(authentication), request);
    }

    @PostMapping("/kick")
    @Operation(summary = "Kick a participant from a session")
    public ApiResponse kickParticipant(
            @Valid @RequestBody KickParticipantRequest request,
            Authentication authentication) {
        collabService.kickParticipant(currentUserId(authentication), request);
        return new ApiResponse("Participant kicked successfully");
    }

    @GetMapping("/active/{projectId}/{fileId}")
    @Operation(summary = "Get the active session for a project file")
    public CollabSessionResponse getActiveSession(
            @PathVariable Long projectId,
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return collabService.getActiveSession(projectId, fileId, authorizationHeader);
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ForbiddenException("Authentication is required");
        }
        return Long.valueOf(authentication.getName());
    }
}
