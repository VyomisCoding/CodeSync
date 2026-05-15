package com.codesync.file.repository;

import com.codesync.file.entity.CodeFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodeFileRepository extends JpaRepository<CodeFile, Long> {

    List<CodeFile> findByProjectIdAndIsDeletedFalse(Long projectId);

    Optional<CodeFile> findByProjectIdAndPathAndIsDeletedFalse(
            Long projectId,
            String path
    );

    boolean existsByProjectIdAndPathAndIsDeletedFalse(
            Long projectId,
            String path
    );

    List<CodeFile> findByProjectIdAndLanguageAndIsDeletedFalse(
            Long projectId,
            String language
    );

    List<CodeFile> findByLastEditedByAndIsDeletedFalse(Long userId);

    Long countByProjectIdAndIsDeletedFalse(Long projectId);

    List<CodeFile> findByProjectIdAndIsDeletedTrue(Long projectId);

    List<CodeFile> findByProjectIdAndNameContainingIgnoreCaseAndIsDeletedFalse(
            Long projectId,
            String keyword
    );

    List<CodeFile> findByProjectIdAndContentContainingIgnoreCaseAndIsDeletedFalse(
            Long projectId,
            String keyword
    );

    List<CodeFile> findByProjectIdAndPathStartingWithAndIsDeletedFalse(
            Long projectId,
            String pathPrefix
    );

    List<CodeFile> findByProjectIdAndPathStartingWithAndIsDeletedTrue(
            Long projectId,
            String pathPrefix
    );
}
