package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationPreviewResult;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceInvitationPreviewServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final String WORKSPACE_NAME = "Knot 팀";
    private static final String REMOTE_ADDRESS = "203.0.113.10";
    private static final String CODE = "X35D3S";
    private static final String CODE_HASH = "code-hash";
    private static final String LINK_TOKEN = "AbC_1234567890";
    private static final String LINK_TOKEN_HASH = "link-token-hash";
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

    private final WorkspaceInvitationRepository workspaceInvitationRepository = mock(
            WorkspaceInvitationRepository.class
    );
    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final WorkspaceInvitationSecretGenerator secretGenerator = mock(WorkspaceInvitationSecretGenerator.class);
    private final WorkspaceInvitationSecretProtector secretProtector = mock(WorkspaceInvitationSecretProtector.class);
    private final WorkspaceInvitationPreviewRateLimiter rateLimiter = mock(WorkspaceInvitationPreviewRateLimiter.class);
    private final WorkspaceInvitationTransactionExecutor transactionExecutor = mock(
            WorkspaceInvitationTransactionExecutor.class
    );
    private final WorkspaceInvitationService service = new WorkspaceInvitationService(
            workspaceRepository,
            workspaceMemberRepository,
            workspaceInvitationRepository,
            secretGenerator,
            secretProtector,
            rateLimiter,
            transactionExecutor,
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            )
    );

    @DisplayName("소문자 초대 코드는 대문자로 정규화해 대상 워크스페이스를 조회한다")
    @Test
    void preview_success_normalizedCode() {
        // given
        WorkspaceInvitation invitation = validInvitation();
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_HASH);
        when(workspaceInvitationRepository.findByInviteCodeHash(CODE_HASH)).thenReturn(Optional.of(invitation));
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));

        // when
        WorkspaceInvitationPreviewResult result = service.preview(
                " x35d3s ",
                REMOTE_ADDRESS
        );

        // then
        assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(result.workspaceName()).isEqualTo(WORKSPACE_NAME);
        verify(rateLimiter).consume(REMOTE_ADDRESS);
        verify(
                workspaceInvitationRepository,
                never()
        ).save(invitation);
    }

    @DisplayName("링크 토큰은 원문 그대로 대소문자를 구분해 대상 워크스페이스를 조회한다")
    @Test
    void preview_success_exactLinkToken() {
        // given
        WorkspaceInvitation invitation = validInvitation();
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        LINK_TOKEN
                )
        ).thenReturn(LINK_TOKEN_HASH);
        when(workspaceInvitationRepository.findByLinkTokenHash(LINK_TOKEN_HASH)).thenReturn(Optional.of(invitation));
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));

        // when
        WorkspaceInvitationPreviewResult result = service.preview(
                LINK_TOKEN,
                REMOTE_ADDRESS
        );

        // then
        assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(result.workspaceName()).isEqualTo(WORKSPACE_NAME);
        verifyNoInteractions(rateLimiter);
    }

    @DisplayName("복호화할 암호문이 없는 V3 초대도 해시와 유효 상태가 맞으면 미리보기에 성공한다")
    @Test
    void preview_success_legacyInvitationWithoutDecrypt() {
        // given
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                WORKSPACE_ID,
                LINK_TOKEN_HASH,
                CODE_HASH,
                NOW.minusSeconds(1)
        );
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_HASH);
        when(workspaceInvitationRepository.findByInviteCodeHash(CODE_HASH)).thenReturn(Optional.of(invitation));
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));

        // when
        WorkspaceInvitationPreviewResult result = service.preview(
                CODE,
                REMOTE_ADDRESS
        );

        // then
        assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(result.workspaceName()).isEqualTo(WORKSPACE_NAME);
        verify(
                secretProtector,
                never()
        ).decrypt(
                any(),
                any(),
                any()
        );
    }

    @DisplayName("금지 문자가 포함된 6자리 값도 코드 조회 제한을 소비한 뒤 초대 없음으로 응답한다")
    @Test
    void preview_failure_invalidCodeConsumesRateLimit() {
        // given
        String invalidCode = "ABC1O0";

        // when
        ThrowingCallable action = () -> service.preview(
                invalidCode,
                REMOTE_ADDRESS
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
        verify(rateLimiter).consume(REMOTE_ADDRESS);
        verifyNoInteractions(secretProtector);
    }

    @DisplayName("존재하지 않는 credential은 원인을 구분하지 않는 초대 없음으로 응답한다")
    @Test
    void preview_failure_invitationNotFound() {
        // given
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_HASH);
        when(workspaceInvitationRepository.findByInviteCodeHash(CODE_HASH)).thenReturn(Optional.empty());

        // when
        ThrowingCallable action = () -> service.preview(
                CODE,
                REMOTE_ADDRESS
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
        verify(rateLimiter).consume(REMOTE_ADDRESS);
    }

    @DisplayName("만료된 초대는 원인을 구분하지 않는 초대 없음으로 응답한다")
    @Test
    void preview_failure_expiredInvitation() {
        // given
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                WORKSPACE_ID,
                LINK_TOKEN_HASH,
                CODE_HASH,
                LINK_TOKEN,
                CODE,
                NOW.minus(WorkspaceInvitation.VALIDITY_PERIOD)
        );
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_HASH);
        when(workspaceInvitationRepository.findByInviteCodeHash(CODE_HASH)).thenReturn(Optional.of(invitation));

        // when
        ThrowingCallable action = () -> service.preview(
                CODE,
                REMOTE_ADDRESS
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
    }

    @DisplayName("재발급으로 무효화된 초대는 원인을 구분하지 않는 초대 없음으로 응답한다")
    @Test
    void preview_failure_invalidatedInvitation() {
        // given
        WorkspaceInvitation invitation = validInvitation();
        invitation.invalidate(NOW);
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_HASH);
        when(workspaceInvitationRepository.findByInviteCodeHash(CODE_HASH)).thenReturn(Optional.of(invitation));

        // when
        ThrowingCallable action = () -> service.preview(
                CODE,
                REMOTE_ADDRESS
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
    }

    private WorkspaceInvitation validInvitation() {
        return WorkspaceInvitation.create(
                WORKSPACE_ID,
                LINK_TOKEN_HASH,
                CODE_HASH,
                LINK_TOKEN,
                CODE,
                NOW.minusSeconds(1)
        );
    }

    private Workspace workspace() {
        return Workspace.create(
                WORKSPACE_NAME,
                NOW
        );
    }
}
