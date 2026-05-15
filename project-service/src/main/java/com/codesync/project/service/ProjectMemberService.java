package com.codesync.project.service;

import java.util.List;

public interface ProjectMemberService {

    boolean isMember(Long projectId, Long userId);

    List<Long> getProjectIdsForMember(Long userId);

    void removeMembersByProjectId(Long projectId);
}
