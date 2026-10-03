package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationPreviewResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationSecrets;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import com.knot.backend.workspace.domain.WorkspaceInvitationSecretCollisionException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceInvitationService {
    static final int MAX_SECRET_GENERATION_ATTEMPTS = 3;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInvitationRepository workspaceInvitationRepository;
    private final WorkspaceInvitationSecretGenerator secretGenerator;
    private final WorkspaceInvitationSecretProtector secretProtector;
    private final WorkspaceInvitationPreviewRateLimiter previewRateLimiter;
    private final WorkspaceInvitationTransactionExecutor transactionExecutor;
    private final Clock clock;

    public WorkspaceInvitationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceInvitationRepository workspaceInvitationRepository,
            WorkspaceInvitationSecretGenerator secretGenerator,
            WorkspaceInvitationSecretProtector secretProtector,
            WorkspaceInvitationPreviewRateLimiter previewRateLimiter,
            WorkspaceInvitationTransactionExecutor transactionExecutor,
            Clock clock
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceInvitationRepository = workspaceInvitationRepository;
        this.secretGenerator = secretGenerator;
        this.secretProtector = secretProtector;
        this.previewRateLimiter = previewRateLimiter;
        this.transactionExecutor = transactionExecutor;
        this.clock = clock;
    }

    public WorkspaceInvitationResult issue(
            Long workspaceId,
            long memberId
    ) {
        return executeWithSecretCollisionRetry(
                () -> issueInTransaction(
                        workspaceId,
                        memberId
                )
        );
    }

    private WorkspaceInvitationResult issueInTransaction(
            Long workspaceId,
            long memberId
    ) {
        validateWorkspaceId(workspaceId);
        validateIssueAllowed(
                workspaceId,
                memberId
        );

        WorkspaceInvitationSecrets secrets = secretGenerator.generate();
        WorkspaceInvitation invitation = workspaceInvitationRepository.save(
                WorkspaceInvitation.create(
                        workspaceId,
                        secretProtector.hash(
                                WorkspaceInvitationSecretKind.LINK_TOKEN,
                                secrets.linkToken()
                        ),
                        secretProtector.hash(
                                WorkspaceInvitationSecretKind.INVITE_CODE,
                                secrets.code()
                        ),
                        currentTime()
                )
        );
        return new WorkspaceInvitationResult(
                secrets.code(),
                secrets.linkToken(),
                invitation.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public WorkspaceInvitationPreviewResult preview(
            String tokenOrCode,
            String remoteAddress
    ) {
        WorkspaceInvitationCredential credential = WorkspaceInvitationCredential.from(tokenOrCode);
        if (credential.rateLimited()) {
            previewRateLimiter.consume(remoteAddress);
            credential.validate();
        }

        WorkspaceInvitation invitation = findValidPreviewInvitation(credential);
        Workspace workspace = workspaceRepository.findById(invitation.getWorkspaceId())
                .orElseThrow(this::previewNotFound);
        return new WorkspaceInvitationPreviewResult(
                invitation.getWorkspaceId(),
                workspace.getName()
        );
    }

    private WorkspaceInvitationResult executeWithSecretCollisionRetry(Supplier<WorkspaceInvitationResult> operation) {
        WorkspaceInvitationSecretCollisionException lastCollision = null;
        for (int attempt = 0; attempt < MAX_SECRET_GENERATION_ATTEMPTS; attempt++) {
            try {
                return transactionExecutor.execute(operation);
            } catch (WorkspaceInvitationSecretCollisionException exception) {
                lastCollision = exception;
            }
        }
        throw lastCollision;
    }

    private WorkspaceInvitation findValidPreviewInvitation(WorkspaceInvitationCredential credential) {
        String secretHash = secretProtector.hash(
                credential.kind(),
                credential.secret()
        );
        return findPreviewInvitation(
                credential,
                secretHash
        ).filter(invitation -> invitation.isValidAt(currentTime()))
                .orElseThrow(this::previewNotFound);
    }

    private Optional<WorkspaceInvitation> findPreviewInvitation(
            WorkspaceInvitationCredential credential,
            String secretHash
    ) {
        if (credential.kind() == WorkspaceInvitationSecretKind.INVITE_CODE) {
            return workspaceInvitationRepository.findByInviteCodeHash(secretHash);
        }
        return workspaceInvitationRepository.findByLinkTokenHash(secretHash);
    }

    private void validateIssueAllowed(
            Long workspaceId,
            long memberId
    ) {
        workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateMembership(
                workspaceId,
                memberId
        );
    }

    private void validateMembership(
            Long workspaceId,
            long memberId
    ) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private WorkspaceException previewNotFound() {
        return new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
    }
}
