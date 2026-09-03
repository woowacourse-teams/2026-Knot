package com.knot.backend.workspace.domain;

import java.util.Optional;

public interface WorkspaceInvitationRepository {

    WorkspaceInvitation save(WorkspaceInvitation workspaceInvitation);

    Optional<WorkspaceInvitation> findByLinkTokenHash(String linkTokenHash);

    Optional<WorkspaceInvitation> findByInviteCodeHash(String inviteCodeHash);

    Optional<Long> findWorkspaceIdByLinkTokenHash(String linkTokenHash);

    Optional<Long> findWorkspaceIdByInviteCodeHash(String inviteCodeHash);

    Optional<WorkspaceInvitation> findUninvalidatedByWorkspaceId(Long workspaceId);
}
