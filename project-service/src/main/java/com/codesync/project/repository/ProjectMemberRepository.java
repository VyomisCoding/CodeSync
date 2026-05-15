package com.codesync.project.repository;

import com.codesync.project.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    List<ProjectMember> findByUserIdOrderByAddedAtDesc(Long userId);

    void deleteByProjectId(Long projectId);
}
