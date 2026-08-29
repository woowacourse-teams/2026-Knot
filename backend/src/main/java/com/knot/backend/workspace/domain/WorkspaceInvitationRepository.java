package com.knot.backend.workspace.domain;

import java.util.Optional;

public interface WorkspaceInvitationRepository {

    WorkspaceInvitation save(WorkspaceInvitation workspaceInvitation);

    Optional<WorkspaceInvitation> findByLinkTokenHash(String linkTokenHash);

    Optional<WorkspaceInvitation> findByInviteCodeHash(String inviteCodeHash);

    Optional<WorkspaceInvitation> findUninvalidatedByWorkspaceId(Long workspaceId);
}
