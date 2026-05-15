package com.codesync.project.service.impl;

import com.codesync.project.repository.ProjectMemberRepository;
import com.codesync.project.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMemberRepository repository;

    @Override
    public boolean isMember(Long projectId, Long userId) {
        return userId != null && repository.existsByProjectIdAndUserId(projectId, userId);
    }

    @Override
    public List<Long> getProjectIdsForMember(Long userId) {
        return repository.findByUserIdOrderByAddedAtDesc(userId)
                .stream()
                .map(member -> member.getProjectId())
                .distinct()
                .toList();
    }

    @Override
    @Transactional
    public void removeMembersByProjectId(Long projectId) {
        repository.deleteByProjectId(projectId);
    }
}
