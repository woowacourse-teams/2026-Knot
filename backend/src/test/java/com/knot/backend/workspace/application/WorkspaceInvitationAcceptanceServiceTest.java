package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationAcceptanceResult;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class WorkspaceInvitationAcceptanceServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final String WORKSPACE_NAME = "Knot 팀";
    private static final String REMOTE_ADDRESS = "203.0.113.10";
    private static final String CODE = "X35D3S";
    private static final String CODE_HASH = "code-hash";
    private static final String LINK_TOKEN = "AbC_1234567890";
    private static final String LINK_TOKEN_HASH = "link-token-hash";
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00.123456789Z");
    private static final Instant CURRENT_TIME = NOW.truncatedTo(ChronoUnit.MICROS);

    private final WorkspaceInvitationRepository workspaceInvitationRepository = mock(
            WorkspaceInvitationRepository.class
    );
    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final WorkspaceInvitationSecretProtector secretProtector = mock(WorkspaceInvitationSecretProtector.class);
    private final WorkspaceInvitationPreviewRateLimiter rateLimiter = mock(WorkspaceInvitationPreviewRateLimiter.class);
    private final WorkspaceInvitationAcceptanceService service = new WorkspaceInvitationAcceptanceService(
            workspaceRepository,
            workspaceMemberRepository,
            workspaceInvitationRepository,
            secretProtector,
            rateLimiter,
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            )
    );

    @DisplayName("초대 코드로 신규 멤버가 워크스페이스에 참여한다")
    @Test
    void accept_success_createsMemberByCode() {
        // given
        WorkspaceInvitation invitation = validInvitation();
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_HASH);
        when(workspaceInvitationRepository.findWorkspaceIdByInviteCodeHash(CODE_HASH))
                .thenReturn(Optional.of(WORKSPACE_ID));
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(workspaceInvitationRepository.findByInviteCodeHash(CODE_HASH)).thenReturn(Optional.of(invitation));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(false);

        // when
        WorkspaceInvitationAcceptanceResult result = service.accept(
                " x35d3s ",
                REMOTE_ADDRESS,
                MEMBER_ID
        );

        // then
        assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(result.workspaceName()).isEqualTo(WORKSPACE_NAME);
        assertThat(result.created()).isTrue();
        ArgumentCaptor<WorkspaceMember> memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue()).extracting(
                WorkspaceMember::getWorkspaceId,
                WorkspaceMember::getMemberId,
                WorkspaceMember::getJoinedAt
        )
                .containsExactly(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        CURRENT_TIME
                );
        InOrder inOrder = inOrder(
                workspaceInvitationRepository,
                workspaceRepository,
                workspaceMemberRepository
        );
        inOrder.verify(workspaceInvitationRepository)
                .findWorkspaceIdByInviteCodeHash(CODE_HASH);
        inOrder.verify(workspaceRepository)
                .findByIdForUpdate(WORKSPACE_ID);
        inOrder.verify(workspaceInvitationRepository)
                .findByInviteCodeHash(CODE_HASH);
        inOrder.verify(workspaceMemberRepository)
                .existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                );
        verify(rateLimiter).consume(REMOTE_ADDRESS);
    }

    @DisplayName("링크 토큰으로 신규 멤버가 워크스페이스에 참여한다")
    @Test
    void accept_success_createsMemberByLinkToken() {
        // given
        WorkspaceInvitation invitation = validInvitation();
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        LINK_TOKEN
                )
        ).thenReturn(LINK_TOKEN_HASH);
        when(workspaceInvitationRepository.findWorkspaceIdByLinkTokenHash(LINK_TOKEN_HASH))
                .thenReturn(Optional.of(WORKSPACE_ID));
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(workspaceInvitationRepository.findByLinkTokenHash(LINK_TOKEN_HASH)).thenReturn(Optional.of(invitation));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(false);

        // when
        WorkspaceInvitationAcceptanceResult result = service.accept(
                LINK_TOKEN,
                REMOTE_ADDRESS,
                MEMBER_ID
        );

        // then
        assertThat(result.created()).isTrue();
        verifyNoInteractions(rateLimiter);
        verify(workspaceMemberRepository).save(any(WorkspaceMember.class));
    }

    @DisplayName("이미 참여 중인 멤버는 멤버십을 추가하지 않고 기존 참여로 응답한다")
    @Test
    void accept_success_existingMember() {
        // given
        WorkspaceInvitation invitation = validInvitation();
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_HASH);
        when(workspaceInvitationRepository.findWorkspaceIdByInviteCodeHash(CODE_HASH))
                .thenReturn(Optional.of(WORKSPACE_ID));
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(workspaceInvitationRepository.findByInviteCodeHash(CODE_HASH)).thenReturn(Optional.of(invitation));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(true);

        // when
        WorkspaceInvitationAcceptanceResult result = service.accept(
                CODE,
                REMOTE_ADDRESS,
                MEMBER_ID
        );

        // then
        assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(result.workspaceName()).isEqualTo(WORKSPACE_NAME);
        assertThat(result.created()).isFalse();
        verify(
                workspaceMemberRepository,
                never()
        ).save(any(WorkspaceMember.class));
    }

    @DisplayName("금지 문자가 포함된 6자리 코드는 조회 제한을 소비한 뒤 초대 없음으로 응답한다")
    @Test
    void accept_failure_invalidCodeConsumesRateLimit() {
        // given
        String invalidCode = "ABC1O0";

        // when
        ThrowingCallable action = () -> service.accept(
                invalidCode,
                REMOTE_ADDRESS,
                MEMBER_ID
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
        verify(rateLimiter).consume(REMOTE_ADDRESS);
        verifyNoInteractions(secretProtector);
    }

    @DisplayName("초기 조회 후 재발급으로 초대가 사라지면 멤버십을 만들지 않고 초대 없음으로 응답한다")
    @Test
    void accept_failure_invitationReissuedAfterWorkspaceLookup() {
        // given
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_HASH);
        when(workspaceInvitationRepository.findWorkspaceIdByInviteCodeHash(CODE_HASH))
                .thenReturn(Optional.of(WORKSPACE_ID));
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(workspaceInvitationRepository.findByInviteCodeHash(CODE_HASH)).thenReturn(Optional.empty());

        // when
        ThrowingCallable action = () -> service.accept(
                CODE,
                REMOTE_ADDRESS,
                MEMBER_ID
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
        verify(
                workspaceMemberRepository,
                never()
        ).existsByWorkspaceIdAndMemberId(
                anyLong(),
                anyLong()
        );
    }

    @DisplayName("만료된 초대는 멤버십보다 먼저 초대 없음으로 응답한다")
    @Test
    void accept_failure_expiredInvitation() {
        // given
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                WORKSPACE_ID,
                LINK_TOKEN_HASH,
                CODE_HASH,
                NOW.minus(WorkspaceInvitation.VALIDITY_PERIOD)
        );
        when(
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        CODE
                )
        ).thenReturn(CODE_HASH);
        when(workspaceInvitationRepository.findWorkspaceIdByInviteCodeHash(CODE_HASH))
                .thenReturn(Optional.of(WORKSPACE_ID));
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(workspaceInvitationRepository.findByInviteCodeHash(CODE_HASH)).thenReturn(Optional.of(invitation));

        // when
        ThrowingCallable action = () -> service.accept(
                CODE,
                REMOTE_ADDRESS,
                MEMBER_ID
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
        verifyNoInteractions(workspaceMemberRepository);
    }

    private WorkspaceInvitation validInvitation() {
        return WorkspaceInvitation.create(
                WORKSPACE_ID,
                LINK_TOKEN_HASH,
                CODE_HASH,
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
