package com.codesync.collab.repository;

import com.codesync.collab.entity.CollabSession;
import com.codesync.collab.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollabSessionRepository extends JpaRepository<CollabSession, UUID> {

    Optional<CollabSession> findBySessionId(UUID sessionId);

    List<CollabSession> findByProjectId(Long projectId);

    List<CollabSession> findByFileId(Long fileId);

    List<CollabSession> findByOwnerId(Long ownerId);

    @Query("""
            select session
            from CollabSession session
            where session.projectId = :projectId
              and session.status = com.codesync.collab.entity.SessionStatus.ACTIVE
            order by session.createdAt desc
            """)
    List<CollabSession> findActiveByProjectId(@Param("projectId") Long projectId);

    @Query("""
            select session
            from CollabSession session
            where session.projectId = :projectId
              and session.fileId = :fileId
              and session.status = com.codesync.collab.entity.SessionStatus.ACTIVE
            """)
    Optional<CollabSession> findActiveByProjectIdAndFileId(
            @Param("projectId") Long projectId,
            @Param("fileId") Long fileId
    );

    @Query("""
            select session
            from CollabSession session
            where session.status = :status
              and session.updatedAt < :threshold
            """)
    List<CollabSession> findByStatusAndUpdatedAtBefore(
            @Param("status") SessionStatus status,
            @Param("threshold") LocalDateTime threshold
    );
}
