package com.codesync.collab.service;

import com.codesync.collab.dto.CodeChangeMessage;
import com.codesync.collab.dto.CollabSessionRequest;
import com.codesync.collab.dto.CollabSessionResponse;
import com.codesync.collab.dto.CursorUpdateRequest;
import com.codesync.collab.dto.EndSessionRequest;
import com.codesync.collab.dto.JoinSessionRequest;
import com.codesync.collab.dto.KickParticipantRequest;
import com.codesync.collab.dto.LeaveSessionRequest;
import com.codesync.collab.dto.ParticipantResponse;

import java.util.List;
import java.util.UUID;

public interface CollabService {

    CollabSessionResponse createSession(Long ownerId, String authorizationHeader, CollabSessionRequest request);

    CollabSessionResponse getSessionById(UUID sessionId, String authorizationHeader);

    List<CollabSessionResponse> getSessionsByProject(Long projectId, String authorizationHeader);

    ParticipantResponse joinSession(Long userId, String authorizationHeader, JoinSessionRequest request);

    void leaveSession(Long userId, LeaveSessionRequest request);

    void endSession(Long userId, EndSessionRequest request);

    List<ParticipantResponse> getParticipants(UUID sessionId, String authorizationHeader);

    ParticipantResponse updateCursor(Long userId, CursorUpdateRequest request);

    void broadcastChange(Long userId, UUID sessionId, CodeChangeMessage message);

    void kickParticipant(Long userId, KickParticipantRequest request);

    CollabSessionResponse getActiveSession(Long projectId, Long fileId, String authorizationHeader);
}
