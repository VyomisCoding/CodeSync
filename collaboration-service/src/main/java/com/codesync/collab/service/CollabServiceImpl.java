package com.codesync.collab.service;

import com.codesync.collab.client.FileAccessClient;
import com.codesync.collab.client.FileSummaryResponse;
import com.codesync.collab.client.ProjectAccessClient;
import com.codesync.collab.client.ProjectSummaryResponse;
import com.codesync.collab.dto.CodeChangeMessage;
import com.codesync.collab.dto.CollabSessionRequest;
import com.codesync.collab.dto.CollabSessionResponse;
import com.codesync.collab.dto.CursorMessage;
import com.codesync.collab.dto.CursorUpdateRequest;
import com.codesync.collab.dto.EndSessionRequest;
import com.codesync.collab.dto.JoinSessionRequest;
import com.codesync.collab.dto.KickParticipantRequest;
import com.codesync.collab.dto.LeaveSessionRequest;
import com.codesync.collab.dto.ParticipantEventMessage;
import com.codesync.collab.dto.ParticipantResponse;
import com.codesync.collab.entity.CollabSession;
import com.codesync.collab.entity.Participant;
import com.codesync.collab.entity.ParticipantRole;
import com.codesync.collab.entity.SessionStatus;
import com.codesync.collab.exception.BadRequestException;
import com.codesync.collab.exception.ConflictException;
import com.codesync.collab.exception.ForbiddenException;
import com.codesync.collab.exception.ResourceNotFoundException;
import com.codesync.collab.repository.CollabSessionRepository;
import com.codesync.collab.repository.ParticipantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class CollabServiceImpl implements CollabService {

    private static final List<String> PARTICIPANT_COLORS = List.of(
            "#EF4444",
            "#F97316",
            "#EAB308",
            "#22C55E",
            "#06B6D4",
            "#3B82F6",
            "#8B5CF6",
            "#EC4899"
    );

    private final CollabSessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final ProjectAccessClient projectAccessClient;
    private final FileAccessClient fileAccessClient;
    private final PasswordEncoder passwordEncoder;
    private final SimpMessagingTemplate messagingTemplate;
    private final long autoEndMinutes;
    private final boolean remoteValidationEnabled;

    public CollabServiceImpl(
            CollabSessionRepository sessionRepository,
            ParticipantRepository participantRepository,
            ProjectAccessClient projectAccessClient,
            FileAccessClient fileAccessClient,
            PasswordEncoder passwordEncoder,
            SimpMessagingTemplate messagingTemplate,
            @Value("${codesync.collab.auto-end-minutes:30}") long autoEndMinutes,
            @Value("${codesync.collab.remote-validation.enabled:true}") boolean remoteValidationEnabled) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.projectAccessClient = projectAccessClient;
        this.fileAccessClient = fileAccessClient;
        this.passwordEncoder = passwordEncoder;
        this.messagingTemplate = messagingTemplate;
        this.autoEndMinutes = autoEndMinutes;
        this.remoteValidationEnabled = remoteValidationEnabled;
    }

    @Override
    @Transactional
    public CollabSessionResponse createSession(Long ownerId, String authorizationHeader, CollabSessionRequest request) {
        validateCreateRequest(request);
        validateWritableSessionTarget(ownerId, authorizationHeader, request.projectId(), request.fileId());

        sessionRepository.findActiveByProjectIdAndFileId(request.projectId(), request.fileId())
                .ifPresent(existing -> {
                    throw new ConflictException("An active collaboration session already exists for this file");
                });

        CollabSession session = new CollabSession();
        session.setProjectId(request.projectId());
        session.setFileId(request.fileId());
        session.setOwnerId(ownerId);
        session.setLanguage(request.language().trim());
        session.setMaxParticipants(request.maxParticipants());
        session.setPasswordProtected(Boolean.TRUE.equals(request.isPasswordProtected()));
        session.setSessionPassword(session.isPasswordProtected()
                ? passwordEncoder.encode(request.sessionPassword())
                : null);
        session.setStatus(SessionStatus.ACTIVE);

        CollabSession savedSession = sessionRepository.save(session);

        Participant host = new Participant();
        host.setSessionId(savedSession.getSessionId());
        host.setUserId(ownerId);
        host.setRole(ParticipantRole.HOST);
        host.setCursorLine(0);
        host.setCursorCol(0);
        host.setColor(colorFor(savedSession.getSessionId(), ownerId));
        participantRepository.save(host);

        return toSessionResponse(savedSession);
    }

    @Override
    @Transactional(readOnly = true)
    public CollabSessionResponse getSessionById(UUID sessionId, String authorizationHeader) {
        CollabSession session = requireSession(sessionId);
        validateReadableSessionTarget(session, authorizationHeader);
        return toSessionResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollabSessionResponse> getSessionsByProject(Long projectId, String authorizationHeader) {
        if (remoteValidationEnabled) {
            assertReadableProject(projectId, authorizationHeader);
        }
        return sessionRepository.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(CollabSession::getCreatedAt).reversed())
                .map(this::toSessionResponse)
                .toList();
    }

    @Override
    @Transactional
    public ParticipantResponse joinSession(Long userId, String authorizationHeader, JoinSessionRequest request) {
        CollabSession session = requireSession(request.sessionId());
        ensureActive(session);
        boolean canEdit = validateJoinTarget(session, userId, authorizationHeader);
        verifyPasswordIfNeeded(session, request.sessionPassword());

        Participant existing = participantRepository.findBySessionIdAndUserIdAndLeftAtIsNull(session.getSessionId(), userId)
                .orElse(null);
        if (existing != null) {
            return toParticipantResponse(existing);
        }

        long participantCount = participantRepository.countParticipants(session.getSessionId());
        if (participantCount >= session.getMaxParticipants()) {
            throw new ConflictException("Session has reached the maxParticipants limit");
        }

        Participant participant = new Participant();
        participant.setSessionId(session.getSessionId());
        participant.setUserId(userId);
        participant.setRole(resolveJoinRole(session, userId, request.role(), canEdit));
        participant.setCursorLine(0);
        participant.setCursorCol(0);
        participant.setColor(colorFor(session.getSessionId(), userId));

        Participant savedParticipant = participantRepository.save(participant);
        touchSession(session);
        broadcastParticipantEvent("JOINED", savedParticipant);
        return toParticipantResponse(savedParticipant);
    }

    @Override
    @Transactional
    public void leaveSession(Long userId, LeaveSessionRequest request) {
        CollabSession session = requireSession(request.sessionId());
        ensureActive(session);

        if (session.getOwnerId().equals(userId)) {
            endSession(userId, new EndSessionRequest(request.sessionId()));
            return;
        }

        Participant participant = requireActiveParticipant(session.getSessionId(), userId);
        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);
        touchSession(session);
        broadcastParticipantEvent("LEFT", participant);
    }

    @Override
    @Transactional
    public void endSession(Long userId, EndSessionRequest request) {
        CollabSession session = requireSession(request.sessionId());
        ensureOwner(session, userId);
        if (session.getStatus() == SessionStatus.ENDED) {
            return;
        }
        endSessionInternal(session, "SESSION_ENDED");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantResponse> getParticipants(UUID sessionId, String authorizationHeader) {
        CollabSession session = requireSession(sessionId);
        validateReadableSessionTarget(session, authorizationHeader);
        return participantRepository.findParticipantsBySessionId(sessionId).stream()
                .map(this::toParticipantResponse)
                .toList();
    }

    @Override
    @Transactional
    public ParticipantResponse updateCursor(Long userId, CursorUpdateRequest request) {
        CollabSession session = requireSession(request.sessionId());
        ensureActive(session);

        Participant participant = requireActiveParticipant(session.getSessionId(), userId);
        participant.setCursorLine(request.cursorLine());
        participant.setCursorCol(request.cursorCol());

        Participant savedParticipant = participantRepository.save(participant);
        touchSession(session);

        messagingTemplate.convertAndSend(
                cursorTopic(session.getSessionId()),
                new CursorMessage(
                        session.getSessionId(),
                        savedParticipant.getUserId(),
                        savedParticipant.getCursorLine(),
                        savedParticipant.getCursorCol(),
                        savedParticipant.getColor(),
                        LocalDateTime.now()
                )
        );

        return toParticipantResponse(savedParticipant);
    }

    @Override
    @Transactional
    public void broadcastChange(Long userId, UUID sessionId, CodeChangeMessage message) {
        CollabSession session = requireSession(sessionId);
        ensureActive(session);

        Participant participant = requireActiveParticipant(session.getSessionId(), userId);
        if (participant.getRole() == ParticipantRole.VIEWER) {
            throw new ForbiddenException("VIEWER participants cannot broadcast code changes");
        }

        touchSession(session);
        messagingTemplate.convertAndSend(
                sessionTopic(session.getSessionId()),
                new CodeChangeMessage(
                        session.getSessionId(),
                        session.getFileId(),
                        userId,
                        session.getLanguage(),
                        message.content(),
                        LocalDateTime.now()
                )
        );
    }

    @Override
    @Transactional
    public void kickParticipant(Long userId, KickParticipantRequest request) {
        CollabSession session = requireSession(request.sessionId());
        ensureActive(session);
        ensureOwner(session, userId);

        Participant participant = participantRepository.findByParticipantIdAndSessionId(
                        request.participantId(),
                        request.sessionId()
                )
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));

        if (participant.getLeftAt() != null) {
            throw new BadRequestException("Participant already left the session");
        }
        if (participant.getUserId().equals(session.getOwnerId()) || participant.getRole() == ParticipantRole.HOST) {
            throw new ForbiddenException("The session owner cannot be kicked");
        }

        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);
        touchSession(session);
        broadcastParticipantEvent("KICKED", participant);
    }

    @Override
    @Transactional(readOnly = true)
    public CollabSessionResponse getActiveSession(Long projectId, Long fileId, String authorizationHeader) {
        if (remoteValidationEnabled) {
            assertReadableProject(projectId, authorizationHeader);
            assertReadableProjectFile(fileId, projectId, authorizationHeader);
        }
        CollabSession session = sessionRepository.findActiveByProjectIdAndFileId(projectId, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Active session not found"));
        return toSessionResponse(session);
    }

    @Scheduled(fixedDelayString = "${codesync.collab.housekeeping-delay-ms:60000}")
    @Transactional
    public void autoEndInactiveSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(autoEndMinutes);
        sessionRepository.findByStatusAndUpdatedAtBefore(SessionStatus.ACTIVE, threshold)
                .forEach(session -> endSessionInternal(session, "SESSION_ENDED_DUE_TO_INACTIVITY"));
    }

    private void validateCreateRequest(CollabSessionRequest request) {
        if (Boolean.TRUE.equals(request.isPasswordProtected())
                && (request.sessionPassword() == null || request.sessionPassword().isBlank())) {
            throw new BadRequestException("sessionPassword is required for password protected sessions");
        }
    }

    private void validateWritableSessionTarget(
            Long actorUserId,
            String authorizationHeader,
            Long projectId,
            Long fileId) {
        if (!remoteValidationEnabled) {
            return;
        }
        assertWritableProjectMember(projectId, actorUserId, authorizationHeader);
        assertReadableProjectFile(fileId, projectId, authorizationHeader);
    }

    private void validateReadableSessionTarget(CollabSession session, String authorizationHeader) {
        if (!remoteValidationEnabled) {
            return;
        }
        assertReadableProject(session.getProjectId(), authorizationHeader);
        assertReadableProjectFile(session.getFileId(), session.getProjectId(), authorizationHeader);
    }

    private boolean validateJoinTarget(CollabSession session, Long userId, String authorizationHeader) {
        if (!remoteValidationEnabled) {
            return true;
        }
        ProjectSummaryResponse project = assertReadableProject(session.getProjectId(), authorizationHeader);
        assertReadableProjectFile(session.getFileId(), session.getProjectId(), authorizationHeader);
        return canWriteProject(project, userId, authorizationHeader);
    }

    private CollabSession requireSession(UUID sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
    }

    private void ensureActive(CollabSession session) {
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new BadRequestException("Session is not active");
        }
    }

    private void ensureOwner(CollabSession session, Long userId) {
        if (!session.getOwnerId().equals(userId)) {
            throw new ForbiddenException("Only the session owner can perform this action");
        }
    }

    private void verifyPasswordIfNeeded(CollabSession session, String rawPassword) {
        if (!session.isPasswordProtected()) {
            return;
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ForbiddenException("Password is required for this session");
        }
        if (!passwordEncoder.matches(rawPassword, session.getSessionPassword())) {
            throw new ForbiddenException("Invalid session password");
        }
    }

    private ParticipantRole resolveJoinRole(
            CollabSession session,
            Long userId,
            ParticipantRole requestedRole,
            boolean canEdit) {
        if (session.getOwnerId().equals(userId)) {
            return ParticipantRole.HOST;
        }
        if (requestedRole == ParticipantRole.HOST) {
            throw new ForbiddenException("Only the session owner can be the HOST");
        }
        if (!canEdit) {
            return ParticipantRole.VIEWER;
        }
        if (requestedRole == null) {
            return ParticipantRole.EDITOR;
        }
        return requestedRole;
    }

    private ProjectSummaryResponse assertReadableProject(Long projectId, String authorizationHeader) {
        return projectAccessClient.getAccessibleProject(projectId, authorizationHeader);
    }

    private ProjectSummaryResponse assertWritableProjectMember(Long projectId, Long actorUserId, String authorizationHeader) {
        if (actorUserId == null) {
            throw new ForbiddenException("Authentication is required");
        }

        ProjectSummaryResponse project = projectAccessClient.getAccessibleProject(projectId, authorizationHeader);
        if (Boolean.TRUE.equals(project.isArchived())) {
            throw new BadRequestException("Archived projects are read-only");
        }
        if (canWriteProject(project, actorUserId, authorizationHeader)) {
            return project;
        }

        throw new ForbiddenException("Only the project owner or members can create collaboration sessions");
    }

    private boolean canWriteProject(ProjectSummaryResponse project, Long actorUserId, String authorizationHeader) {
        if (actorUserId == null) {
            return false;
        }
        if (Boolean.TRUE.equals(project.isArchived())) {
            return false;
        }
        if (actorUserId.equals(project.ownerId())) {
            return true;
        }
        return projectAccessClient.isProjectMember(project.projectId(), actorUserId, authorizationHeader);
    }

    private FileSummaryResponse assertReadableProjectFile(Long fileId, Long expectedProjectId, String authorizationHeader) {
        FileSummaryResponse file = fileAccessClient.getReadableFile(fileId, authorizationHeader);
        if (!expectedProjectId.equals(file.projectId())) {
            throw new BadRequestException("fileId does not belong to the supplied projectId");
        }
        if (!"FILE".equalsIgnoreCase(file.type())) {
            throw new BadRequestException("Collaboration sessions can only be created for files");
        }
        return file;
    }

    private Participant requireActiveParticipant(UUID sessionId, Long userId) {
        return participantRepository.findBySessionIdAndUserIdAndLeftAtIsNull(sessionId, userId)
                .orElseThrow(() -> new ForbiddenException("User is not an active participant in this session"));
    }

    private void touchSession(CollabSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    private void endSessionInternal(CollabSession session, String eventName) {
        if (session.getStatus() == SessionStatus.ENDED) {
            return;
        }

        LocalDateTime endedAt = LocalDateTime.now();
        participantRepository.findParticipantsBySessionId(session.getSessionId()).stream()
                .filter(participant -> participant.getLeftAt() == null)
                .forEach(participant -> {
                    participant.setLeftAt(endedAt);
                    participantRepository.save(participant);
                    broadcastParticipantEvent(eventName, participant);
                });

        session.setStatus(SessionStatus.ENDED);
        session.setEndedAt(endedAt);
        session.setUpdatedAt(endedAt);
        sessionRepository.save(session);

        messagingTemplate.convertAndSend(
                participantsTopic(session.getSessionId()),
                new ParticipantEventMessage(session.getSessionId(), eventName, null, endedAt)
        );
    }

    private void broadcastParticipantEvent(String eventName, Participant participant) {
        messagingTemplate.convertAndSend(
                participantsTopic(participant.getSessionId()),
                new ParticipantEventMessage(
                        participant.getSessionId(),
                        eventName,
                        toParticipantResponse(participant),
                        LocalDateTime.now()
                )
        );
    }

    private CollabSessionResponse toSessionResponse(CollabSession session) {
        return new CollabSessionResponse(
                session.getSessionId(),
                session.getProjectId(),
                session.getFileId(),
                session.getOwnerId(),
                session.getStatus(),
                session.getLanguage(),
                session.getCreatedAt(),
                session.getEndedAt(),
                session.getMaxParticipants(),
                session.isPasswordProtected(),
                participantRepository.countParticipants(session.getSessionId())
        );
    }

    private ParticipantResponse toParticipantResponse(Participant participant) {
        return new ParticipantResponse(
                participant.getParticipantId(),
                participant.getSessionId(),
                participant.getUserId(),
                participant.getRole(),
                participant.getJoinedAt(),
                participant.getLeftAt(),
                participant.getCursorLine(),
                participant.getCursorCol(),
                participant.getColor()
        );
    }

    private String colorFor(UUID sessionId, Long userId) {
        int index = Math.abs((sessionId.toString() + ":" + userId).hashCode()) % PARTICIPANT_COLORS.size();
        return PARTICIPANT_COLORS.get(index);
    }

    private String sessionTopic(UUID sessionId) {
        return "/topic/session/" + sessionId;
    }

    private String cursorTopic(UUID sessionId) {
        return "/topic/cursor/" + sessionId;
    }

    private String participantsTopic(UUID sessionId) {
        return "/topic/participants/" + sessionId;
    }
}
