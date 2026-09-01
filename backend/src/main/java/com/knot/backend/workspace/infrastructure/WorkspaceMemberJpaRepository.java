package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceMemberJpaRepository extends JpaRepository<WorkspaceMember, Long> {

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
