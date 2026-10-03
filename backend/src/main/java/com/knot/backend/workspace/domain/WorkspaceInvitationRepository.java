package com.knot.backend.workspace.domain;

import java.util.Optional;
import java.util.List;
import java.time.Instant;

public interface WorkspaceInvitationRepository {

    List<WorkspaceInvitation> findValidByWorkspaceIdAt(
            Long workspaceId,
            Instant now
    );

    WorkspaceInvitation save(WorkspaceInvitation workspaceInvitation);

    Optional<WorkspaceInvitation> findByLinkTokenHash(String linkTokenHash);

    Optional<WorkspaceInvitation> findByInviteCodeHash(String inviteCodeHash);

    Optional<Long> findWorkspaceIdByLinkTokenHash(String linkTokenHash);

    Optional<Long> findWorkspaceIdByInviteCodeHash(String inviteCodeHash);

    Optional<WorkspaceInvitation> findUninvalidatedByWorkspaceId(Long workspaceId);
}
