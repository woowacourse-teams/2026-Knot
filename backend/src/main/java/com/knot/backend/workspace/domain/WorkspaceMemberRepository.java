package com.knot.backend.workspace.domain;

import java.util.Optional;

public interface WorkspaceMemberRepository {

    WorkspaceMember save(WorkspaceMember workspaceMember);

    Optional<WorkspaceMember> findById(Long workspaceMemberId);

    boolean existsByWorkspaceIdAndMemberId(
            Long workspaceId,
            Long memberId
    );
}
