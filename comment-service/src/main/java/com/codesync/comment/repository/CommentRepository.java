package com.codesync.comment.repository;

import com.codesync.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Optional<Comment> findByCommentId(Long commentId);

    List<Comment> findByProjectId(Long projectId);

    List<Comment> findByFileId(Long fileId);

    List<Comment> findByAuthorId(Long authorId);

    List<Comment> findByParentCommentId(Long parentCommentId);

    List<Comment> findByResolved(Boolean resolved);

    List<Comment> findByLineNumber(Integer lineNumber);

    List<Comment> findByFileIdAndLineNumber(Long fileId, Integer lineNumber);

    long countByFileId(Long fileId);

    void deleteByCommentId(Long commentId);
}
