package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationAcceptanceResult;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkspaceInvitationAcceptanceService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInvitationRepository workspaceInvitationRepository;
    private final WorkspaceInvitationSecretProtector secretProtector;
    private final WorkspaceInvitationPreviewRateLimiter rateLimiter;
    private final Clock clock;

    public WorkspaceInvitationAcceptanceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceInvitationRepository workspaceInvitationRepository,
            WorkspaceInvitationSecretProtector secretProtector,
            WorkspaceInvitationPreviewRateLimiter rateLimiter,
            Clock clock
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceInvitationRepository = workspaceInvitationRepository;
        this.secretProtector = secretProtector;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public WorkspaceInvitationAcceptanceResult accept(
            String credentialValue,
            String remoteAddress,
            long memberId
    ) {
        WorkspaceInvitationCredential credential = WorkspaceInvitationCredential.from(credentialValue);
        if (credential.rateLimited()) {
            rateLimiter.consume(remoteAddress);
            credential.validate();
        }

        String secretHash = secretProtector.hash(
                credential.kind(),
                credential.secret()
        );
        WorkspaceInvitation invitation = findInvitation(
                credential,
                secretHash
        ).orElseThrow(this::notFound);
        Workspace workspace = workspaceRepository.findByIdForUpdate(invitation.getWorkspaceId())
                .orElseThrow(this::notFound);
        Instant now = currentTime();
        if (!invitation.isValidAt(now)) {
            throw notFound();
        }
        boolean created = joinWorkspace(
                invitation.getWorkspaceId(),
                memberId,
                now
        );
        return new WorkspaceInvitationAcceptanceResult(
                invitation.getWorkspaceId(),
                workspace.getName(),
                created
        );
    }

    private Optional<WorkspaceInvitation> findInvitation(
            WorkspaceInvitationCredential credential,
            String secretHash
    ) {
        if (credential.kind() == WorkspaceInvitationSecretKind.INVITE_CODE) {
            return workspaceInvitationRepository.findByInviteCodeHash(secretHash);
        }
        return workspaceInvitationRepository.findByLinkTokenHash(secretHash);
    }

    private boolean joinWorkspace(
            Long workspaceId,
            long memberId,
            Instant joinedAt
    ) {
        if (workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            return false;
        }
        workspaceMemberRepository.save(
                WorkspaceMember.create(
                        workspaceId,
                        memberId,
                        WorkspaceMemberRole.MEMBER,
                        joinedAt
                )
        );
        return true;
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private WorkspaceException notFound() {
        return new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
    }
}
