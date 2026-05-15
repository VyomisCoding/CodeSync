package com.codesync.execution.repository;

import com.codesync.execution.entity.ExecutionJob;
import com.codesync.execution.entity.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, UUID> {

    Optional<ExecutionJob> findByJobId(UUID jobId);

    List<ExecutionJob> findByUserId(Long userId);

    List<ExecutionJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ExecutionJob> findByProjectId(Long projectId);

    List<ExecutionJob> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<ExecutionJob> findByStatus(ExecutionStatus status);

    List<ExecutionJob> findByLanguage(String language);

    List<ExecutionJob> findByLanguageIgnoreCase(String language);

    List<ExecutionJob> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByUserId(Long userId);
}
