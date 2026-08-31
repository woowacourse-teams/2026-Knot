package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import com.knot.backend.workspace.domain.WorkspaceInvitationSecretCollisionException;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceInvitationRepositoryAdapter implements WorkspaceInvitationRepository {
    private static final Set<String> SECRET_UNIQUE_CONSTRAINTS = Set.of(
            "uk_workspace_invitations_link_token_hash",
            "uk_workspace_invitations_invite_code_hash"
    );
    private final WorkspaceInvitationJpaRepository workspaceInvitationJpaRepository;

    public WorkspaceInvitationRepositoryAdapter(WorkspaceInvitationJpaRepository workspaceInvitationJpaRepository) {
        this.workspaceInvitationJpaRepository = workspaceInvitationJpaRepository;
    }

    @Override
    public WorkspaceInvitation save(WorkspaceInvitation workspaceInvitation) {
        try {
            return workspaceInvitationJpaRepository.saveAndFlush(workspaceInvitation);
        } catch (DataIntegrityViolationException exception) {
            if (isSecretUniqueConstraintViolation(exception)) {
                throw new WorkspaceInvitationSecretCollisionException(exception);
            }
            throw exception;
        }
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

    private boolean isSecretUniqueConstraintViolation(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException) {
                return SECRET_UNIQUE_CONSTRAINTS.contains(constraintViolationException.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}
