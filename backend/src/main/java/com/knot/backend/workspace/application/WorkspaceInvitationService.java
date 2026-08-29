package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationSecrets;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkspaceInvitationService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInvitationRepository workspaceInvitationRepository;
    private final WorkspaceInvitationSecretGenerator secretGenerator;
    private final WorkspaceInvitationSecretProtector secretProtector;
    private final Clock clock;

    public WorkspaceInvitationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceInvitationRepository workspaceInvitationRepository,
            WorkspaceInvitationSecretGenerator secretGenerator,
            WorkspaceInvitationSecretProtector secretProtector,
            Clock clock
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceInvitationRepository = workspaceInvitationRepository;
        this.secretGenerator = secretGenerator;
        this.secretProtector = secretProtector;
        this.clock = clock;
    }

    @Transactional
    public WorkspaceInvitationResult issue(
            Long workspaceId,
            long memberId
    ) {
        validateWorkspaceId(workspaceId);
        validateIssueAllowedWithLock(
                workspaceId,
                memberId
        );

        Instant now = currentTime();
        return workspaceInvitationRepository.findUninvalidatedByWorkspaceId(workspaceId)
                .map(
                        invitation -> issueWithExistingInvitation(
                                invitation,
                                now
                        )
                )
                .orElseGet(
                        () -> createInvitation(
                                workspaceId,
                                now
                        )
                );
    }

    public WorkspaceInvitationResult get(
            Long workspaceId,
            long memberId
    ) {
        validateWorkspaceId(workspaceId);
        validateAccessAllowed(
                workspaceId,
                memberId
        );

        Instant now = currentTime();
        WorkspaceInvitation invitation = workspaceInvitationRepository.findUninvalidatedByWorkspaceId(workspaceId)
                .filter(candidate -> candidate.isValidAt(now))
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_NOT_FOUND));

        return existingInvitationResult(invitation);
    }

    @Transactional
    public WorkspaceInvitationResult reissue(
            Long workspaceId,
            long memberId
    ) {
        validateWorkspaceId(workspaceId);
        validateIssueAllowedWithLock(
                workspaceId,
                memberId
        );

        Instant now = currentTime();
        PreparedInvitation preparedInvitation = prepareInvitation(
                workspaceId,
                now
        );
        workspaceInvitationRepository.findUninvalidatedByWorkspaceId(workspaceId)
                .ifPresent(invitation -> {
                    invitation.invalidate(now);
                    workspaceInvitationRepository.save(invitation);
                });
        return savePreparedInvitation(preparedInvitation);
    }

    private WorkspaceInvitationResult issueWithExistingInvitation(
            WorkspaceInvitation invitation,
            Instant now
    ) {
        if (invitation.isValidAt(now)) {
            return existingInvitationResult(invitation);
        }
        PreparedInvitation preparedInvitation = prepareInvitation(
                invitation.getWorkspaceId(),
                now
        );
        invitation.invalidate(now);
        workspaceInvitationRepository.save(invitation);
        return savePreparedInvitation(preparedInvitation);
    }

    private WorkspaceInvitationResult createInvitation(
            Long workspaceId,
            Instant now
    ) {
        return savePreparedInvitation(
                prepareInvitation(
                        workspaceId,
                        now
                )
        );
    }

    private PreparedInvitation prepareInvitation(
            Long workspaceId,
            Instant now
    ) {
        WorkspaceInvitationSecrets secrets = secretGenerator.generate();
        String linkTokenHash = secretProtector.hash(
                WorkspaceInvitationSecretKind.LINK_TOKEN,
                secrets.linkToken()
        );
        String inviteCodeHash = secretProtector.hash(
                WorkspaceInvitationSecretKind.INVITE_CODE,
                secrets.code()
        );
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                workspaceId,
                linkTokenHash,
                inviteCodeHash,
                secretProtector.encrypt(
                        workspaceId,
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        secrets.linkToken()
                ),
                secretProtector.encrypt(
                        workspaceId,
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        secrets.code()
                ),
                now
        );
        return new PreparedInvitation(
                invitation,
                secrets
        );
    }

    private WorkspaceInvitationResult savePreparedInvitation(PreparedInvitation preparedInvitation) {
        WorkspaceInvitation savedInvitation = workspaceInvitationRepository.save(preparedInvitation.invitation());
        return new WorkspaceInvitationResult(
                preparedInvitation.secrets()
                        .code(),
                preparedInvitation.secrets()
                        .linkToken(),
                savedInvitation.getExpiresAt(),
                true
        );
    }

    private WorkspaceInvitationResult existingInvitationResult(WorkspaceInvitation invitation) {
        if (!invitation.hasRecoverableSecrets()) {
            throw secretRecoveryFailed();
        }
        String inviteCode = secretProtector.decrypt(
                invitation.getWorkspaceId(),
                WorkspaceInvitationSecretKind.INVITE_CODE,
                invitation.getInviteCodeCiphertext()
        );
        String linkToken = secretProtector.decrypt(
                invitation.getWorkspaceId(),
                WorkspaceInvitationSecretKind.LINK_TOKEN,
                invitation.getLinkTokenCiphertext()
        );
        validateRecoveredSecrets(
                invitation,
                inviteCode,
                linkToken
        );
        return new WorkspaceInvitationResult(
                inviteCode,
                linkToken,
                invitation.getExpiresAt(),
                false
        );
    }

    private void validateRecoveredSecrets(
            WorkspaceInvitation invitation,
            String inviteCode,
            String linkToken
    ) {
        boolean inviteCodeMatches = secretProtector.matches(
                WorkspaceInvitationSecretKind.INVITE_CODE,
                inviteCode,
                invitation.getInviteCodeHash()
        );
        boolean linkTokenMatches = secretProtector.matches(
                WorkspaceInvitationSecretKind.LINK_TOKEN,
                linkToken,
                invitation.getLinkTokenHash()
        );
        if (!inviteCodeMatches || !linkTokenMatches) {
            throw secretRecoveryFailed();
        }
    }

    private void validateIssueAllowedWithLock(
            Long workspaceId,
            long memberId
    ) {
        workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateMembership(
                workspaceId,
                memberId
        );
    }

    private void validateAccessAllowed(
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

    private WorkspaceException secretRecoveryFailed() {
        return new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED);
    }

    private record PreparedInvitation(
            WorkspaceInvitation invitation,
            WorkspaceInvitationSecrets secrets
    ) {
    }
}
