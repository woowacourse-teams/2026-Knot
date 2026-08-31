package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.WorkspaceInvitation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface WorkspaceInvitationJpaRepository extends JpaRepository<WorkspaceInvitation, Long> {

    Optional<WorkspaceInvitation> findByLinkTokenHash(String linkTokenHash);

    Optional<WorkspaceInvitation> findByInviteCodeHash(String inviteCodeHash);

    @Query("""
            select invitation.workspaceId
            from WorkspaceInvitation invitation
            where invitation.linkTokenHash = :linkTokenHash
            """)
    Optional<Long> findWorkspaceIdByLinkTokenHash(String linkTokenHash);

    @Query("""
            select invitation.workspaceId
            from WorkspaceInvitation invitation
            where invitation.inviteCodeHash = :inviteCodeHash
            """)
    Optional<Long> findWorkspaceIdByInviteCodeHash(String inviteCodeHash);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndInvalidatedAtIsNull(Long workspaceId);
}
