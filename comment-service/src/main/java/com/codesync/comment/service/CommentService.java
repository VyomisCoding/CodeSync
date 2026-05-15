package com.codesync.comment.service;

import com.codesync.comment.dto.CommentResponse;
import com.codesync.comment.dto.CreateCommentRequest;
import com.codesync.comment.dto.UpdateCommentRequest;

import java.util.List;

public interface CommentService {

    CommentResponse addComment(CreateCommentRequest request, Long actorUserId, String authorizationHeader);

    CommentResponse getCommentById(Long commentId, String authorizationHeader);

    List<CommentResponse> getCommentsByFile(Long fileId, String authorizationHeader);

    List<CommentResponse> getCommentsByProject(Long projectId, String authorizationHeader);

    List<CommentResponse> getReplies(Long commentId, String authorizationHeader);

    CommentResponse updateComment(
            Long commentId,
            UpdateCommentRequest request,
            Long actorUserId,
            boolean admin,
            String authorizationHeader);

    void deleteComment(Long commentId, Long actorUserId, boolean admin, String authorizationHeader);

    CommentResponse resolveComment(Long commentId, Long actorUserId, boolean admin, String authorizationHeader);

    CommentResponse unresolveComment(Long commentId, Long actorUserId, boolean admin, String authorizationHeader);

    List<CommentResponse> getCommentsByLine(Long fileId, Integer lineNumber, String authorizationHeader);

    long countComments(Long fileId, String authorizationHeader);
}
