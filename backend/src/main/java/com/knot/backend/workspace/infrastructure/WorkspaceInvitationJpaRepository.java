package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.WorkspaceInvitation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceInvitationJpaRepository extends JpaRepository<WorkspaceInvitation, Long> {

    Optional<WorkspaceInvitation> findByLinkTokenHash(String linkTokenHash);

    Optional<WorkspaceInvitation> findByInviteCodeHash(String inviteCodeHash);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndInvalidatedAtIsNull(Long workspaceId);
}
