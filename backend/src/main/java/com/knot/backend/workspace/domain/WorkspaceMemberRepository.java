package com.knot.backend.workspace.domain;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository {

    WorkspaceMember save(WorkspaceMember workspaceMember);

    Optional<WorkspaceMember> findById(Long workspaceMemberId);

    List<WorkspaceMember> findAllByMemberIdForUpdate(Long memberId);

    Optional<WorkspaceMember> findLastViewedByMemberId(Long memberId);

    List<WorkspaceMember> saveAll(List<WorkspaceMember> workspaceMembers);

    void flush();

    boolean existsByWorkspaceIdAndMemberId(
            Long workspaceId,
            Long memberId
    );
}
