package com.codesync.version.repository;

import com.codesync.version.entity.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

    List<Snapshot> findByProjectId(Long projectId);

    List<Snapshot> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<Snapshot> findByFileId(Long fileId);

    List<Snapshot> findByFileIdOrderByCreatedAtDesc(Long fileId);

    List<Snapshot> findByAuthorId(Long authorId);

    List<Snapshot> findByBranch(String branch);

    List<Snapshot> findByBranchOrderByCreatedAtDesc(String branch);

    Optional<Snapshot> findBySnapshotId(Long snapshotId);

    Optional<Snapshot> findByHash(String hash);

    Optional<Snapshot> findByTag(String tag);

    Optional<Snapshot> findTopByFileIdOrderByCreatedAtDesc(Long fileId);

    Optional<Snapshot> findTopByFileIdAndBranchOrderByCreatedAtDesc(Long fileId, String branch);

    default Optional<Snapshot> findLatestByFileId(Long fileId) {
        return findTopByFileIdOrderByCreatedAtDesc(fileId);
    }
}
