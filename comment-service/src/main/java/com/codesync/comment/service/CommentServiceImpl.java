package com.codesync.comment.service;

import com.codesync.comment.client.FileAccessClient;
import com.codesync.comment.client.FileSummaryResponse;
import com.codesync.comment.client.MentionNotificationClient;
import com.codesync.comment.client.ProjectAccessClient;
import com.codesync.comment.client.ProjectSummaryResponse;
import com.codesync.comment.client.SnapshotAccessClient;
import com.codesync.comment.client.SnapshotSummaryResponse;
import com.codesync.comment.dto.CommentResponse;
import com.codesync.comment.dto.CreateCommentRequest;
import com.codesync.comment.dto.UpdateCommentRequest;
import com.codesync.comment.entity.Comment;
import com.codesync.comment.exception.BadRequestException;
import com.codesync.comment.exception.ForbiddenException;
import com.codesync.comment.exception.ResourceNotFoundException;
import com.codesync.comment.exception.UnauthorizedException;
import com.codesync.comment.repository.CommentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]+)");
    private static final Comparator<Comment> COMMENT_ORDER =
            Comparator.comparing(Comment::getCreatedAt).thenComparing(Comment::getCommentId);

    private final CommentRepository commentRepository;
    private final ProjectAccessClient projectAccessClient;
    private final FileAccessClient fileAccessClient;
    private final SnapshotAccessClient snapshotAccessClient;
    private final MentionNotificationClient mentionNotificationClient;
    private final boolean remoteValidationEnabled;
    private final boolean mentionNotificationsEnabled;

    public CommentServiceImpl(
            CommentRepository commentRepository,
            ProjectAccessClient projectAccessClient,
            FileAccessClient fileAccessClient,
            SnapshotAccessClient snapshotAccessClient,
            MentionNotificationClient mentionNotificationClient,
            @Value("${codesync.comment.remote-validation.enabled:true}") boolean remoteValidationEnabled,
            @Value("${codesync.comment.mention-notifications.enabled:false}") boolean mentionNotificationsEnabled) {
        this.commentRepository = commentRepository;
        this.projectAccessClient = projectAccessClient;
        this.fileAccessClient = fileAccessClient;
        this.snapshotAccessClient = snapshotAccessClient;
        this.mentionNotificationClient = mentionNotificationClient;
        this.remoteValidationEnabled = remoteValidationEnabled;
        this.mentionNotificationsEnabled = mentionNotificationsEnabled;
    }

    @Override
    @Transactional
    public CommentResponse addComment(CreateCommentRequest request, Long actorUserId, String authorizationHeader) {
        ensureAuthenticated(actorUserId);
        validateRequest(request);

        ProjectSummaryResponse project = validateProjectAccess(request.projectId(), authorizationHeader);
        FileSummaryResponse file = validateFileAccess(request.fileId(), authorizationHeader);
        ensureFileBelongsToProject(file, request.projectId());
        validateSnapshot(request.snapshotId(), request.projectId(), request.fileId(), authorizationHeader);

        if (project != null && project.projectId() != null && !Objects.equals(project.projectId(), request.projectId())) {
            throw new IllegalStateException("Project service returned mismatched project data");
        }

        validateReplyThread(request, authorizationHeader);

        Comment comment = new Comment();
        comment.setProjectId(request.projectId());
        comment.setFileId(request.fileId());
        comment.setAuthorId(actorUserId);
        comment.setContent(normalizeContent(request.content()));
        comment.setLineNumber(request.lineNumber());
        comment.setColumnNumber(request.columnNumber());
        comment.setParentCommentId(request.parentCommentId());
        comment.setResolved(false);
        comment.setSnapshotId(request.snapshotId());

        Comment savedComment = commentRepository.save(comment);
        dispatchMentionNotifications(savedComment, authorizationHeader);
        return CommentResponse.fromEntity(savedComment);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentResponse getCommentById(Long commentId, String authorizationHeader) {
        Comment comment = getCommentEntity(commentId);
        validateFileAccess(comment.getFileId(), authorizationHeader);
        return CommentResponse.fromEntity(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByFile(Long fileId, String authorizationHeader) {
        validateFileAccess(fileId, authorizationHeader);
        return commentRepository.findByFileId(fileId).stream()
                .sorted(COMMENT_ORDER)
                .map(CommentResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByProject(Long projectId, String authorizationHeader) {
        validateProjectAccess(projectId, authorizationHeader);
        return commentRepository.findByProjectId(projectId).stream()
                .sorted(COMMENT_ORDER)
                .map(CommentResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getReplies(Long commentId, String authorizationHeader) {
        Comment parent = getCommentEntity(commentId);
        validateFileAccess(parent.getFileId(), authorizationHeader);
        return commentRepository.findByParentCommentId(commentId).stream()
                .sorted(COMMENT_ORDER)
                .map(CommentResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public CommentResponse updateComment(
            Long commentId,
            UpdateCommentRequest request,
            Long actorUserId,
            boolean admin,
            String authorizationHeader) {
        ensureAuthenticated(actorUserId);
        Comment comment = getCommentEntity(commentId);
        validateFileAccess(comment.getFileId(), authorizationHeader);
        ensureCanMutate(comment, actorUserId, admin);

        comment.setContent(normalizeContent(request.content()));
        Comment updated = commentRepository.save(comment);
        dispatchMentionNotifications(updated, authorizationHeader);
        return CommentResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long actorUserId, boolean admin, String authorizationHeader) {
        ensureAuthenticated(actorUserId);
        Comment comment = getCommentEntity(commentId);
        validateFileAccess(comment.getFileId(), authorizationHeader);
        ensureCanMutate(comment, actorUserId, admin);

        List<Comment> replies = commentRepository.findByParentCommentId(commentId);
        if (!replies.isEmpty()) {
            commentRepository.deleteAll(replies);
        }
        commentRepository.deleteByCommentId(commentId);
    }

    @Override
    @Transactional
    public CommentResponse resolveComment(Long commentId, Long actorUserId, boolean admin, String authorizationHeader) {
        ensureAuthenticated(actorUserId);
        Comment comment = getCommentEntity(commentId);
        validateFileAccess(comment.getFileId(), authorizationHeader);
        ensureCanMutate(comment, actorUserId, admin);
        comment.setResolved(true);
        return CommentResponse.fromEntity(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public CommentResponse unresolveComment(Long commentId, Long actorUserId, boolean admin, String authorizationHeader) {
        ensureAuthenticated(actorUserId);
        Comment comment = getCommentEntity(commentId);
        validateFileAccess(comment.getFileId(), authorizationHeader);
        ensureCanMutate(comment, actorUserId, admin);
        comment.setResolved(false);
        return CommentResponse.fromEntity(commentRepository.save(comment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByLine(Long fileId, Integer lineNumber, String authorizationHeader) {
        if (lineNumber == null || lineNumber <= 0) {
            throw new BadRequestException("lineNumber must be positive");
        }
        validateFileAccess(fileId, authorizationHeader);
        return commentRepository.findByFileIdAndLineNumber(fileId, lineNumber).stream()
                .sorted(COMMENT_ORDER)
                .map(CommentResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countComments(Long fileId, String authorizationHeader) {
        validateFileAccess(fileId, authorizationHeader);
        return commentRepository.countByFileId(fileId);
    }

    private Comment getCommentEntity(Long commentId) {
        return commentRepository.findByCommentId(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
    }

    private void validateRequest(CreateCommentRequest request) {
        if (request.columnNumber() != null && request.lineNumber() == null) {
            throw new BadRequestException("columnNumber requires lineNumber");
        }
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            throw new BadRequestException("content must not be blank");
        }
        return normalized;
    }

    private void validateReplyThread(CreateCommentRequest request, String authorizationHeader) {
        if (request.parentCommentId() == null) {
            return;
        }

        Comment parentComment = getCommentEntity(request.parentCommentId());
        validateFileAccess(parentComment.getFileId(), authorizationHeader);

        if (!Objects.equals(parentComment.getProjectId(), request.projectId())) {
            throw new BadRequestException("Reply must belong to the same project as the parent comment");
        }
        if (!Objects.equals(parentComment.getFileId(), request.fileId())) {
            throw new BadRequestException("Reply must belong to the same file as the parent comment");
        }
        if (parentComment.getParentCommentId() != null) {
            throw new BadRequestException("Nested replies beyond one level are not supported");
        }
    }

    private void ensureCanMutate(Comment comment, Long actorUserId, boolean admin) {
        if (admin) {
            return;
        }
        if (!Objects.equals(comment.getAuthorId(), actorUserId)) {
            throw new ForbiddenException("Only the comment author or an admin can modify this comment");
        }
    }

    private void ensureAuthenticated(Long actorUserId) {
        if (actorUserId == null) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    private ProjectSummaryResponse validateProjectAccess(Long projectId, String authorizationHeader) {
        if (!remoteValidationEnabled) {
            return null;
        }
        return projectAccessClient.getAccessibleProject(projectId, authorizationHeader);
    }

    private FileSummaryResponse validateFileAccess(Long fileId, String authorizationHeader) {
        if (!remoteValidationEnabled) {
            return null;
        }
        return fileAccessClient.getReadableFile(fileId, authorizationHeader);
    }

    private void validateSnapshot(Long snapshotId, Long projectId, Long fileId, String authorizationHeader) {
        if (snapshotId == null || !remoteValidationEnabled) {
            return;
        }

        SnapshotSummaryResponse snapshot = snapshotAccessClient.getSnapshot(snapshotId, authorizationHeader);
        if (!Objects.equals(snapshot.projectId(), projectId)) {
            throw new BadRequestException("snapshotId does not belong to the supplied projectId");
        }
        if (!Objects.equals(snapshot.fileId(), fileId)) {
            throw new BadRequestException("snapshotId does not belong to the supplied fileId");
        }
    }

    private void ensureFileBelongsToProject(FileSummaryResponse file, Long projectId) {
        if (file == null || file.projectId() == null) {
            return;
        }
        if (!Objects.equals(file.projectId(), projectId)) {
            throw new BadRequestException("fileId does not belong to the supplied projectId");
        }
        if (Boolean.TRUE.equals(file.isDeleted())) {
            throw new BadRequestException("Cannot comment on a deleted file");
        }
    }

    private void dispatchMentionNotifications(Comment comment, String authorizationHeader) {
        if (!mentionNotificationsEnabled) {
            return;
        }

        Set<String> mentions = extractMentions(comment.getContent());
        for (String username : mentions) {
            try {
                mentionNotificationClient.sendMentionNotification(
                        Map.of(
                                "type", "MENTION",
                                "actorId", comment.getAuthorId(),
                                "mentionedUsername", username,
                                "relatedId", comment.getCommentId(),
                                "relatedType", "COMMENT",
                                "projectId", comment.getProjectId(),
                                "fileId", comment.getFileId(),
                                "url", "/projects/" + comment.getProjectId() + "/files/" + comment.getFileId()
                                        + "?commentId=" + comment.getCommentId()
                        ),
                        authorizationHeader
                );
            } catch (Exception ex) {
                log.warn("Failed to dispatch mention notification for @{}", username, ex);
            }
        }
    }

    private Set<String> extractMentions(String content) {
        Set<String> mentions = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content == null ? "" : content);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }
}
