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
    private static final String CODE = "ABCXYZ";
    private static final String LINK_TOKEN = "link-token";
    private static final String CODE_HASH = "code-hash";
    private static final String LINK_TOKEN_HASH = "link-token-hash";

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

    @DisplayName("초대를 발급하면 새 초대를 저장하고 원문을 반환한다")
    @Test
    void issue_success_createsInvitation() {
        // given
        allowMemberWithWorkspaceLock();
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
        allowMemberWithWorkspaceLock();
        prepareNewInvitation();

        // when
        WorkspaceInvitationResult result = service.issue(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result.code()).isEqualTo(CODE);
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

    private void allowMemberWithWorkspaceLock() {
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
        when(workspaceInvitationRepository.save(any(WorkspaceInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
