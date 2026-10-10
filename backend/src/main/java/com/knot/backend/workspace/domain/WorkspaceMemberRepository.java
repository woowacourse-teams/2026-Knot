package com.knot.backend.workspace.domain;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository {

    WorkspaceMember save(WorkspaceMember workspaceMember);

    Optional<WorkspaceMember> findById(Long workspaceMemberId);

    List<WorkspaceMember> findAllByMemberIdForUpdate(Long memberId);

    List<WorkspaceMember> findAllActiveByWorkspaceIdAndMemberIdsForUpdate(
            Long workspaceId,
            List<Long> memberIds
    );

    Optional<WorkspaceMember> findLastViewedByMemberId(Long memberId);

    Optional<WorkspaceMember> findLatestByWorkspaceIdAndMemberIdForUpdate(
            Long workspaceId,
            Long memberId
    );

    long countActiveByWorkspaceId(Long workspaceId);

    List<Long> findActiveMemberIdsByWorkspaceId(long workspaceId);

    List<WorkspaceMember> saveAll(List<WorkspaceMember> workspaceMembers);

    void flush();

    boolean existsByWorkspaceIdAndMemberId(
            Long workspaceId,
            Long memberId
    );

    boolean existsByWorkspaceIdAndMemberIdAndRole(
            Long workspaceId,
            Long memberId,
            WorkspaceMemberRole role
    );
}
