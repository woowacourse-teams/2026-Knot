package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class WorkspaceInvitationServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00.123456789Z");
    private static final Instant CURRENT_TIME = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final String CODE = "ABC234";
    private static final String LINK_TOKEN = "link-token";
    private static final String CODE_HASH = "code-hash";
    private static final String LINK_TOKEN_HASH = "link-token-hash";
    private static final String CODE_CIPHERTEXT = "code-ciphertext";
    private static final String LINK_TOKEN_CIPHERTEXT = "link-token-ciphertext";

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final WorkspaceInvitationRepository workspaceInvitationRepository = mock(
            WorkspaceInvitationRepository.class
    );
    private final WorkspaceInvitationSecretGenerator secretGenerator = mock(WorkspaceInvitationSecretGenerator.class);
    private final WorkspaceInvitationSecretProtector secretProtector = mock(WorkspaceInvitationSecretProtector.class);
    private final WorkspaceInvitationPreviewRateLimiter previewRateLimiter = mock(
            WorkspaceInvitationPreviewRateLimiter.class
    );
    private final WorkspaceInvitationTransactionExecutor transactionExecutor = mock(
            WorkspaceInvitationTransactionExecutor.class
    );
    private final WorkspaceInvitationService service = new WorkspaceInvitationService(
            workspaceRepository,
            workspaceMemberRepository,
            workspaceInvitationRepository,
            secretGenerator,
            secretProtector,
            previewRateLimiter,
            transactionExecutor,
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            )
    );

    WorkspaceInvitationServiceTest() {
        when(transactionExecutor.execute(any())).thenAnswer(
                invocation -> invocation.<Supplier<WorkspaceInvitationResult>>getArgument(0)
                        .get()
        );
    }

    @DisplayName("활성 초대가 없으면 새 초대를 발급한다")
    @Test
    void issue_success_createsInvitation() {
        // given
        allowMemberWithLock();
        when(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.empty());
        prepareNewInvitation();

        // when
        WorkspaceInvitationResult result = service.issue(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result.code()).isEqualTo(CODE);
        assertThat(result.linkToken()).isEqualTo(LINK_TOKEN);
        assertThat(result.expiresAt()).isEqualTo(CURRENT_TIME.plus(WorkspaceInvitation.VALIDITY_PERIOD));
        assertThat(result.created()).isTrue();
        ArgumentCaptor<WorkspaceInvitation> invitationCaptor = ArgumentCaptor.forClass(WorkspaceInvitation.class);
        verify(workspaceInvitationRepository).save(invitationCaptor.capture());
        assertThat(
                invitationCaptor.getValue()
                        .getInviteCodeHash()
        ).isEqualTo(CODE_HASH);
        assertThat(
                invitationCaptor.getValue()
                        .getLinkTokenHash()
        ).isEqualTo(LINK_TOKEN_HASH);
    }

    @DisplayName("초대 secret 충돌이 발생하면 새 트랜잭션으로 다시 시도한다")
    @Test
    void issue_success_retriesSecretCollision() {
        // given
        WorkspaceInvitationSecretCollisionException collision = new WorkspaceInvitationSecretCollisionException(
                new DataIntegrityViolationException("secret collision")
        );
        doThrow(collision).doAnswer(
                invocation -> invocation.<Supplier<WorkspaceInvitationResult>>getArgument(0)
                        .get()
        )
                .when(transactionExecutor)
                .execute(any());
        allowMemberWithLock();
        when(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.empty());
        prepareNewInvitation();

        // when
        WorkspaceInvitationResult result = service.issue(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result.created()).isTrue();
        verify(
                transactionExecutor,
                times(2)
        ).execute(any());
    }

    @DisplayName("초대 secret 충돌은 최대 세 번까지만 시도한다")
    @Test
    void issue_failure_stopsAfterSecretCollisionRetryLimit() {
        // given
        WorkspaceInvitationSecretCollisionException collision = new WorkspaceInvitationSecretCollisionException(
                new DataIntegrityViolationException("secret collision")
        );
        doThrow(collision).when(transactionExecutor)
                .execute(any());

        // when
        Throwable thrown = catchThrowable(
                () -> service.issue(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        );

        // then
        assertThat(thrown).isSameAs(collision);
        verify(
                transactionExecutor,
                times(WorkspaceInvitationService.MAX_SECRET_GENERATION_ATTEMPTS)
        ).execute(any());
    }

    @DisplayName("secret 충돌이 아닌 무결성 오류는 재시도하지 않는다")
    @Test
    void issue_failure_doesNotRetryOtherIntegrityViolation() {
        // given
        DataIntegrityViolationException integrityViolation = new DataIntegrityViolationException(
                "foreign key violation"
        );
        doThrow(integrityViolation).when(transactionExecutor)
                .execute(any());

        // when
        Throwable thrown = catchThrowable(
                () -> service.issue(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        );

        // then
        assertThat(thrown).isSameAs(integrityViolation);
        verify(transactionExecutor).execute(any());
    }

    @DisplayName("유효한 활성 초대가 있으면 새로 만들지 않고 같은 원문을 반환한다")
    @Test
    void issue_success_returnsExistingInvitation() {
        // given
        allowMemberWithLock();
        WorkspaceInvitation invitation = recoverableInvitation(NOW.minusSeconds(1));
        when(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(WORKSPACE_ID))
                .thenReturn(Optional.of(invitation));
        prepareRecovery(invitation);

        // when
        WorkspaceInvitationResult result = service.issue(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result.code()).isEqualTo(CODE);
        assertThat(result.linkToken()).isEqualTo(LINK_TOKEN);
        assertThat(result.created()).isFalse();
        verify(
                secretGenerator,
                never()
        ).generate();
        verify(
                workspaceInvitationRepository,
                never()
        ).save(any());
    }

    @DisplayName("만료된 V3 초대는 복호화하지 않고 무효화한 뒤 새 초대로 교체한다")
    @Test
    void issue_success_replacesExpiredLegacyInvitation() {
        // given
        allowMemberWithLock();
        WorkspaceInvitation expiredInvitation = WorkspaceInvitation.create(
                WORKSPACE_ID,
                "expired-link-hash",
                "expired-code-hash",
                NOW.minus(WorkspaceInvitation.VALIDITY_PERIOD)
                        .minusSeconds(1)
        );
        when(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(WORKSPACE_ID))
                .thenReturn(Optional.of(expiredInvitation));
        prepareNewInvitation();

        // when
        WorkspaceInvitationResult result = service.issue(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result.created()).isTrue();
        assertThat(expiredInvitation.getInvalidatedAt()).isEqualTo(CURRENT_TIME);
        verify(
                secretProtector,
                never()
        ).decrypt(
                any(),
                any(),
                any()
        );
    }

    @DisplayName("활성 V3 초대는 상태를 바꾸지 않고 복구 실패로 응답한다")
    @Test
    void get_failure_legacyInvitation() {
        // given
        allowMemberWithoutLock();
        WorkspaceInvitation legacyInvitation = WorkspaceInvitation.create(
                WORKSPACE_ID,
                LINK_TOKEN_HASH,
                CODE_HASH,
                NOW.minusSeconds(1)
        );
        when(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(WORKSPACE_ID))
                .thenReturn(Optional.of(legacyInvitation));

        // when
        Throwable thrown = catchThrowable(
                () -> service.get(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                WorkspaceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED)
        );
        assertThat(legacyInvitation.getInvalidatedAt()).isNull();
        verify(
                workspaceInvitationRepository,
                never()
        ).save(any());
    }

    @DisplayName("복호화한 원문의 lookup hash가 저장값과 다르면 복구를 거부한다")
    @Test
    void get_failure_lookupHashMismatch() {
        // given
        allowMemberWithoutLock();
        WorkspaceInvitation invitation = recoverableInvitation(NOW.minusSeconds(1));
        when(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(WORKSPACE_ID))
                .thenReturn(Optional.of(invitation));
        prepareRecovery(invitation);
        when(
                secretProtector.matches(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE,
                        CODE_HASH
                )
        ).thenReturn(false);

        // when
        Throwable thrown = catchThrowable(
                () -> service.get(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                WorkspaceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED)
        );
        verify(
                workspaceInvitationRepository,
                never()
        ).save(any());
    }

    @DisplayName("복호화한 링크 토큰의 lookup hash가 저장값과 다르면 복구를 거부한다")
    @Test
    void get_failure_linkTokenHashMismatch() {
        // given
        allowMemberWithoutLock();
        WorkspaceInvitation invitation = recoverableInvitation(NOW.minusSeconds(1));
        when(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(WORKSPACE_ID))
                .thenReturn(Optional.of(invitation));
        prepareRecovery(invitation);
        when(
                secretProtector.matches(
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        LINK_TOKEN,
                        LINK_TOKEN_HASH
                )
        ).thenReturn(false);

        // when
        Throwable thrown = catchThrowable(
                () -> service.get(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                WorkspaceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED)
        );
        verify(
                workspaceInvitationRepository,
                never()
        ).save(any());
    }

    @DisplayName("명시적 재발급은 기존 초대를 무효화하고 새 초대를 만든다")
    @Test
    void reissue_success_replacesInvitation() {
        // given
        allowMemberWithLock();
        WorkspaceInvitation existingInvitation = recoverableInvitation(NOW.minusSeconds(1));
        when(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(WORKSPACE_ID))
                .thenReturn(Optional.of(existingInvitation));
        prepareNewInvitation();

        // when
        WorkspaceInvitationResult result = service.reissue(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(existingInvitation.getInvalidatedAt()).isEqualTo(CURRENT_TIME);
        assertThat(result.code()).isEqualTo(CODE);
        assertThat(result.linkToken()).isEqualTo(LINK_TOKEN);
        assertThat(result.created()).isTrue();
    }

    @DisplayName("새 초대 secret 준비에 실패하면 기존 초대를 무효화하지 않는다")
    @Test
    void reissue_failure_preservesInvitationBeforeMutation() {
        // given
        allowMemberWithLock();
        WorkspaceInvitation existingInvitation = recoverableInvitation(NOW.minusSeconds(1));
        when(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(WORKSPACE_ID))
                .thenReturn(Optional.of(existingInvitation));
        when(secretGenerator.generate())
                .thenThrow(new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED));

        // when
        Throwable thrown = catchThrowable(
                () -> service.reissue(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        );

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class);
        assertThat(existingInvitation.getInvalidatedAt()).isNull();
        verify(
                workspaceInvitationRepository,
                never()
        ).save(any());
    }

    @DisplayName("워크스페이스 멤버가 아니면 초대를 발급하지 않는다")
    @Test
    void issue_failure_nonMember() {
        // given
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "Knot 팀",
                                NOW
                        )
                )
        );
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(false);

        // when
        Throwable thrown = catchThrowable(
                () -> service.issue(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                WorkspaceException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED)
        );
        verify(
                secretGenerator,
                never()
        ).generate();
    }

    private void allowMemberWithLock() {
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "Knot 팀",
                                NOW
                        )
                )
        );
        allowMembership();
    }

    private void allowMemberWithoutLock() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "Knot 팀",
                                NOW
                        )
                )
        );
        allowMembership();
    }

    private void allowMembership() {
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(true);
    }

    private void prepareNewInvitation() {
        when(secretGenerator.generate()).thenReturn(
                new WorkspaceInvitationSecrets(
                        CODE,
                        LINK_TOKEN
                )
        );
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_HASH);
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        LINK_TOKEN
                )
        ).thenReturn(LINK_TOKEN_HASH);
        when(
                secretProtector.encrypt(
                        WORKSPACE_ID,
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_CIPHERTEXT);
        when(
                secretProtector.encrypt(
                        WORKSPACE_ID,
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        LINK_TOKEN
                )
        ).thenReturn(LINK_TOKEN_CIPHERTEXT);
        when(workspaceInvitationRepository.save(any(WorkspaceInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private WorkspaceInvitation recoverableInvitation(Instant createdAt) {
        return WorkspaceInvitation.create(
                WORKSPACE_ID,
                LINK_TOKEN_HASH,
                CODE_HASH,
                LINK_TOKEN_CIPHERTEXT,
                CODE_CIPHERTEXT,
                createdAt
        );
    }

    private void prepareRecovery(WorkspaceInvitation invitation) {
        when(
                secretProtector.decrypt(
                        WORKSPACE_ID,
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        invitation.getInviteCodeCiphertext()
                )
        ).thenReturn(CODE);
        when(
                secretProtector.decrypt(
                        WORKSPACE_ID,
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        invitation.getLinkTokenCiphertext()
                )
        ).thenReturn(LINK_TOKEN);
        when(
                secretProtector.matches(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE,
                        CODE_HASH
                )
        ).thenReturn(true);
        when(
                secretProtector.matches(
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        LINK_TOKEN,
                        LINK_TOKEN_HASH
                )
        ).thenReturn(true);
    }
}
