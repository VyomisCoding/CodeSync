package com.codesync.comment.controller;

import com.codesync.comment.dto.CommentResponse;
import com.codesync.comment.dto.CreateCommentRequest;
import com.codesync.comment.dto.UpdateCommentRequest;
import com.codesync.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import java.util.Map;

@RestController
@RequestMapping("/comments")
@Tag(name = "Comment Service", description = "Inline code review, replies, and resolve workflow APIs")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/add")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an inline comment or reply", security = @SecurityRequirement(name = "bearerAuth"))
    public CommentResponse addComment(
            @Valid @RequestBody CreateCommentRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return commentService.addComment(request, currentUserId(authentication), authorizationHeader);
    }

    @GetMapping("/{commentId}")
    @Operation(summary = "Get a comment by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public CommentResponse getComment(
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return commentService.getCommentById(commentId, authorizationHeader);
    }

    @GetMapping("/file/{fileId}")
    @Operation(summary = "List comments for a file", security = @SecurityRequirement(name = "bearerAuth"))
    public List<CommentResponse> getCommentsByFile(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return commentService.getCommentsByFile(fileId, authorizationHeader);
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "List comments for a project", security = @SecurityRequirement(name = "bearerAuth"))
    public List<CommentResponse> getCommentsByProject(
            @PathVariable Long projectId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return commentService.getCommentsByProject(projectId, authorizationHeader);
    }

    @GetMapping("/replies/{commentId}")
    @Operation(summary = "List direct replies for a comment", security = @SecurityRequirement(name = "bearerAuth"))
    public List<CommentResponse> getReplies(
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return commentService.getReplies(commentId, authorizationHeader);
    }

    @PutMapping("/update/{commentId}")
    @Operation(summary = "Update the content of an existing comment", security = @SecurityRequirement(name = "bearerAuth"))
    public CommentResponse updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return commentService.updateComment(
                commentId,
                request,
                currentUserId(authentication),
                isAdmin(authentication),
                authorizationHeader
        );
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete a comment", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long commentId,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        commentService.deleteComment(
                commentId,
                currentUserId(authentication),
                isAdmin(authentication),
                authorizationHeader
        );
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/resolve/{commentId}")
    @Operation(summary = "Mark a comment as resolved", security = @SecurityRequirement(name = "bearerAuth"))
    public CommentResponse resolveComment(
            @PathVariable Long commentId,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return commentService.resolveComment(
                commentId,
                currentUserId(authentication),
                isAdmin(authentication),
                authorizationHeader
        );
    }

    @PutMapping("/unresolve/{commentId}")
    @Operation(summary = "Reopen a resolved comment", security = @SecurityRequirement(name = "bearerAuth"))
    public CommentResponse unresolveComment(
            @PathVariable Long commentId,
            Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader) {
        return commentService.unresolveComment(
                commentId,
                currentUserId(authentication),
                isAdmin(authentication),
                authorizationHeader
        );
    }

    @GetMapping("/line/{fileId}/{lineNumber}")
    @Operation(summary = "List comments anchored to a specific file line", security = @SecurityRequirement(name = "bearerAuth"))
    public List<CommentResponse> getCommentsByLine(
            @PathVariable Long fileId,
            @PathVariable Integer lineNumber,
            @RequestHeader("Authorization") String authorizationHeader) {
        return commentService.getCommentsByLine(fileId, lineNumber, authorizationHeader);
    }

    @GetMapping("/count/{fileId}")
    @Operation(summary = "Count comments for a file", security = @SecurityRequirement(name = "bearerAuth"))
    public Map<String, Long> countComments(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return Map.of(
                "fileId", fileId,
                "count", commentService.countComments(fileId, authorizationHeader)
        );
    }

    private Long currentUserId(Authentication authentication) {
        return authentication == null ? null : Long.valueOf(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
