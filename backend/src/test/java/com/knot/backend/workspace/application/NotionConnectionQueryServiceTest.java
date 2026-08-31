package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.NotionConnectionStatusResult;
import com.knot.backend.workspace.domain.NotionConnection;
import com.knot.backend.workspace.domain.NotionConnectionRepository;
import com.knot.backend.workspace.domain.NotionConnectionStatus;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Instant;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotionConnectionQueryServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long OWNER_ID = 3L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final NotionConnectionRepository connectionRepository = mock(NotionConnectionRepository.class);
    private final NotionConnectionQueryService service = new NotionConnectionQueryService(
            workspaceRepository,
            workspaceMemberRepository,
            connectionRepository
    );

    @DisplayName("워크스페이스 멤버가 connection이 없는 워크스페이스를 조회하면 NOT_CONNECTED를 반환한다")
    @Test
    void findStatus_success_notConnected() {
        // given
        allowMember();
        when(connectionRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.empty());

        // when
        NotionConnectionStatusResult result = service.findStatus(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result.status()).isEqualTo(NotionConnectionStatus.NOT_CONNECTED);
    }

    @DisplayName("인증한 멤버가 현재 OWNER이면 CONNECTED를 반환한다")
    @Test
    void findStatus_success_connected() {
        // given
        allowMember();
        NotionConnection connection = connection(OWNER_ID);
        when(connectionRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.of(connection));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        OWNER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(true);

        // when
        NotionConnectionStatusResult result = service.findStatus(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result.status()).isEqualTo(NotionConnectionStatus.CONNECTED);
    }

    @DisplayName("인증한 멤버가 더 이상 OWNER가 아니면 REAUTH_REQUIRED를 반환한다")
    @Test
    void findStatus_success_reauthRequired() {
        // given
        allowMember();
        NotionConnection connection = connection(OWNER_ID);
        when(connectionRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.of(connection));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        OWNER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(false);

        // when
        NotionConnectionStatusResult result = service.findStatus(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result.status()).isEqualTo(NotionConnectionStatus.REAUTH_REQUIRED);
    }

    @DisplayName("워크스페이스 멤버가 아니면 connection 상태를 조회할 수 없다")
    @Test
    void findStatus_failure_nonMember() {
        // given
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "Knot 팀",
                                CREATED_AT
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
        ThrowingCallable action = () -> service.findStatus(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        verifyNoInteractions(connectionRepository);
    }

    private void allowMember() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "Knot 팀",
                                CREATED_AT
                        )
                )
        );
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(true);
    }

    private NotionConnection connection(Long authorizingMemberId) {
        return NotionConnection.create(
                WORKSPACE_ID,
                "access-envelope",
                null,
                "notion-workspace-id",
                "Knot Notion",
                null,
                "bot-id",
                "user",
                "notion-owner-user-id",
                null,
                null,
                authorizingMemberId,
                CREATED_AT
        );
    }
}
