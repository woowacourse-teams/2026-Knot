package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class WorkspaceLeaveServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-30T00:00:00Z");
    private static final Instant LEFT_AT = Instant.parse("2026-09-30T00:01:00.123456Z");

    @DisplayName("MEMBER가 탈퇴하면 멤버십에 탈퇴 시각을 기록하고 마지막 조회 상태를 해제한다")
    @Test
    void leave_success_member() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceLeaveService service = service(
                workspaceRepository,
                workspaceMemberRepository
        );
        Workspace workspace = workspace();
        WorkspaceMember actor = workspaceMember(WorkspaceMemberRole.MEMBER);
        actor.markLastViewed();
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workspace));
        when(
                workspaceMemberRepository.findLatestByWorkspaceIdAndMemberIdForUpdate(
                        1L,
                        2L
                )
        ).thenReturn(Optional.of(actor));
        when(workspaceMemberRepository.countActiveByWorkspaceId(1L)).thenReturn(2L);

        // when
        service.leave(
                2L,
                1L
        );

        // then
        assertThat(actor.getLeftAt()).isEqualTo(LEFT_AT);
        assertThat(actor.isLastViewed()).isFalse();
        assertThat(workspace.isDeleted()).isFalse();
        verify(
                workspaceRepository,
                never()
        ).save(any(Workspace.class));
        verify(workspaceMemberRepository).save(actor);
    }

    @DisplayName("마지막 활성 멤버가 탈퇴하면 워크스페이스도 같은 시각으로 논리 삭제한다")
    @Test
    void leave_success_lastActiveMemberDeletesWorkspace() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceLeaveService service = service(
                workspaceRepository,
                workspaceMemberRepository
        );
        Workspace workspace = workspace();
        WorkspaceMember actor = workspaceMember(WorkspaceMemberRole.OWNER);
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workspace));
        when(
                workspaceMemberRepository.findLatestByWorkspaceIdAndMemberIdForUpdate(
                        1L,
                        2L
                )
        ).thenReturn(Optional.of(actor));
        when(workspaceMemberRepository.countActiveByWorkspaceId(1L)).thenReturn(1L);

        // when
        service.leave(
                2L,
                1L
        );

        // then
        assertThat(actor.getLeftAt()).isEqualTo(LEFT_AT);
        assertThat(workspace.getDeletedAt()).isEqualTo(LEFT_AT);
        InOrder inOrder = inOrder(
                workspaceRepository,
                workspaceMemberRepository
        );
        inOrder.verify(workspaceRepository)
                .findByIdForUpdate(1L);
        inOrder.verify(workspaceMemberRepository)
                .findLatestByWorkspaceIdAndMemberIdForUpdate(
                        1L,
                        2L
                );
        inOrder.verify(workspaceMemberRepository)
                .countActiveByWorkspaceId(1L);
        inOrder.verify(workspaceRepository)
                .save(workspace);
        inOrder.verify(workspaceMemberRepository)
                .save(actor);
    }

    @DisplayName("다른 활성 멤버가 있는 OWNER는 일반 탈퇴를 거절한다")
    @Test
    void leave_failure_ownerTransferRequired() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceLeaveService service = service(
                workspaceRepository,
                workspaceMemberRepository
        );
        WorkspaceMember actor = workspaceMember(WorkspaceMemberRole.OWNER);
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workspace()));
        when(
                workspaceMemberRepository.findLatestByWorkspaceIdAndMemberIdForUpdate(
                        1L,
                        2L
                )
        ).thenReturn(Optional.of(actor));
        when(workspaceMemberRepository.countActiveByWorkspaceId(1L)).thenReturn(2L);

        // when
        ThrowingCallable action = () -> service.leave(
                2L,
                1L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_TRANSFER_REQUIRED);
        verify(
                workspaceRepository,
                never()
        ).save(any(Workspace.class));
        verify(
                workspaceMemberRepository,
                never()
        ).save(any(WorkspaceMember.class));
    }

    @DisplayName("이미 탈퇴한 이력이 있으면 다시 탈퇴해도 상태를 바꾸지 않는다")
    @Test
    void leave_success_alreadyLeft() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceLeaveService service = service(
                workspaceRepository,
                workspaceMemberRepository
        );
        WorkspaceMember actor = workspaceMember(WorkspaceMemberRole.MEMBER);
        actor.leave(
                LEFT_AT,
                2L
        );
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workspace()));
        when(
                workspaceMemberRepository.findLatestByWorkspaceIdAndMemberIdForUpdate(
                        1L,
                        2L
                )
        ).thenReturn(Optional.of(actor));

        // when
        service.leave(
                2L,
                1L
        );

        // then
        verify(
                workspaceMemberRepository,
                never()
        ).countActiveByWorkspaceId(1L);
        verify(
                workspaceMemberRepository,
                never()
        ).save(any(WorkspaceMember.class));
    }

    @DisplayName("워크스페이스가 없거나 삭제되었으면 404 오류를 반환한다")
    @Test
    void leave_failure_workspaceNotFound() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceLeaveService service = service(
                workspaceRepository,
                workspaceMemberRepository
        );
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        // when
        ThrowingCallable action = () -> service.leave(
                2L,
                1L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        verifyNoInteractions(workspaceMemberRepository);
    }

    @DisplayName("참여 이력이 없으면 403 오류를 반환한다")
    @Test
    void leave_failure_membershipHistoryNotFound() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceLeaveService service = service(
                workspaceRepository,
                workspaceMemberRepository
        );
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workspace()));
        when(
                workspaceMemberRepository.findLatestByWorkspaceIdAndMemberIdForUpdate(
                        1L,
                        2L
                )
        ).thenReturn(Optional.empty());

        // when
        ThrowingCallable action = () -> service.leave(
                2L,
                1L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        verify(
                workspaceMemberRepository,
                never()
        ).countActiveByWorkspaceId(1L);
    }

    private WorkspaceLeaveService service(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository
    ) {
        return new WorkspaceLeaveService(
                workspaceRepository,
                workspaceMemberRepository,
                Clock.fixed(
                        LEFT_AT,
                        ZoneOffset.UTC
                )
        );
    }

    private Workspace workspace() {
        return Workspace.create(
                "Knot 팀",
                CREATED_AT
        );
    }

    private WorkspaceMember workspaceMember(WorkspaceMemberRole role) {
        return WorkspaceMember.create(
                1L,
                2L,
                role,
                CREATED_AT
        );
    }
}
