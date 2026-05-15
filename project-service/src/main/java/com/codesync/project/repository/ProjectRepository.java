package com.codesync.project.repository;

import com.codesync.project.entity.Project;
import com.codesync.project.entity.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerIdAndIsArchivedFalse(Long ownerId);
    List<Project> findByOwnerIdAndVisibilityAndIsArchivedFalse(Long ownerId, Visibility visibility);

    Optional<Project> findByProjectId(Long projectId);

    List<Project> findByVisibilityAndIsArchivedFalse(Visibility visibility);

    List<Project> findByProjectIdInAndIsArchivedFalseOrderByUpdatedAtDesc(List<Long> projectIds);

    List<Project> findByLanguageIgnoreCaseAndVisibilityAndIsArchivedFalse(
            String language,
            Visibility visibility
    );

    List<Project> findByIsArchived(Boolean isArchived);

    long countByOwnerId(Long ownerId);

    // search by name
    @Query("""
        SELECT p FROM Project p
        WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        AND p.isArchived = false
        AND p.visibility = :visibility
    """)
    List<Project> searchPublicByName(
            @Param("keyword") String keyword,
            @Param("visibility") Visibility visibility
    );
}
