package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class WorkspaceLastViewedServiceTest {
    private static final Instant JOINED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @DisplayName("기존 마지막 조회 상태를 해제한 뒤 요청한 멤버십을 마지막 조회 상태로 저장한다")
    @Test
    void update_success() {
        // given
        WorkspaceMemberRepository repository = mock(WorkspaceMemberRepository.class);
        WorkspaceLastViewedService service = new WorkspaceLastViewedService(repository);
        WorkspaceMember previous = workspaceMember(
                1L,
                10L
        );
        previous.markLastViewed();
        WorkspaceMember target = workspaceMember(
                2L,
                10L
        );
        List<WorkspaceMember> workspaceMembers = List.of(
                previous,
                target
        );
        when(repository.findAllByMemberIdForUpdate(10L)).thenReturn(workspaceMembers);

        // when
        service.update(
                10L,
                2L
        );

        // then
        assertThat(previous.isLastViewed()).isFalse();
        assertThat(target.isLastViewed()).isTrue();
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository)
                .findAllByMemberIdForUpdate(10L);
        inOrder.verify(repository)
                .saveAll(workspaceMembers);
        inOrder.verify(repository)
                .flush();
        inOrder.verify(repository)
                .save(target);
    }

    @DisplayName("같은 워크스페이스를 반복 설정해도 마지막 조회 상태 하나를 유지한다")
    @Test
    void update_success_idempotent() {
        // given
        WorkspaceMemberRepository repository = mock(WorkspaceMemberRepository.class);
        WorkspaceLastViewedService service = new WorkspaceLastViewedService(repository);
        WorkspaceMember target = workspaceMember(
                2L,
                10L
        );
        target.markLastViewed();
        List<WorkspaceMember> workspaceMembers = List.of(target);
        when(repository.findAllByMemberIdForUpdate(10L)).thenReturn(workspaceMembers);

        // when
        service.update(
                10L,
                2L
        );
        service.update(
                10L,
                2L
        );

        // then
        assertThat(target.isLastViewed()).isTrue();
        verify(
                repository,
                org.mockito.Mockito.times(2)
        ).flush();
        verify(
                repository,
                org.mockito.Mockito.times(2)
        ).save(target);
    }

    @DisplayName("요청한 워크스페이스의 멤버가 아니면 존재 여부를 숨긴 404 오류를 반환한다")
    @Test
    void update_failure_workspaceNotFound() {
        // given
        WorkspaceMemberRepository repository = mock(WorkspaceMemberRepository.class);
        WorkspaceLastViewedService service = new WorkspaceLastViewedService(repository);
        WorkspaceMember otherWorkspaceMember = workspaceMember(
                1L,
                10L
        );
        when(repository.findAllByMemberIdForUpdate(10L)).thenReturn(List.of(otherWorkspaceMember));

        // when
        ThrowingCallable action = () -> service.update(
                10L,
                2L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        verify(
                repository,
                never()
        ).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(
                repository,
                never()
        ).flush();
        verify(
                repository,
                never()
        ).save(org.mockito.ArgumentMatchers.any());
    }

    @DisplayName("워크스페이스 ID가 양수가 아니면 저장을 시도하지 않는다")
    @Test
    void update_failure_invalidWorkspaceId() {
        // given
        WorkspaceMemberRepository repository = mock(WorkspaceMemberRepository.class);
        WorkspaceLastViewedService service = new WorkspaceLastViewedService(repository);

        // when
        ThrowingCallable action = () -> service.update(
                10L,
                0L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        verifyNoInteractions(repository);
    }

    private WorkspaceMember workspaceMember(
            long workspaceId,
            long memberId
    ) {
        return WorkspaceMember.create(
                workspaceId,
                memberId,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
    }
}
