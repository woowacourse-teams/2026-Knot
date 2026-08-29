package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceInvitationRepositoryAdapter implements WorkspaceInvitationRepository {
    private final WorkspaceInvitationJpaRepository workspaceInvitationJpaRepository;

    public WorkspaceInvitationRepositoryAdapter(WorkspaceInvitationJpaRepository workspaceInvitationJpaRepository) {
        this.workspaceInvitationJpaRepository = workspaceInvitationJpaRepository;
    }

    @Override
    public WorkspaceInvitation save(WorkspaceInvitation workspaceInvitation) {
        return workspaceInvitationJpaRepository.saveAndFlush(workspaceInvitation);
    }

    @Override
    public Optional<WorkspaceInvitation> findByLinkTokenHash(String linkTokenHash) {
        return workspaceInvitationJpaRepository.findByLinkTokenHash(linkTokenHash);
    }

    @Override
    public Optional<WorkspaceInvitation> findByInviteCodeHash(String inviteCodeHash) {
        return workspaceInvitationJpaRepository.findByInviteCodeHash(inviteCodeHash);
    }

    @Override
    public Optional<WorkspaceInvitation> findUninvalidatedByWorkspaceId(Long workspaceId) {
        return workspaceInvitationJpaRepository.findByWorkspaceIdAndInvalidatedAtIsNull(workspaceId);
    }
}
