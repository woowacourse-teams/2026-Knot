package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.WorkspaceDetailResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceListResult;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceQueryServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-29T00:00:00Z");

    @Test
    @DisplayName("워크스페이스 멤버는 워크스페이스 이름을 조회한다")
    void findDetail_success() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                workspaceRepository,
                workspaceMemberRepository
        );
        Workspace workspace = Workspace.create(
                "Knot 팀",
                CREATED_AT
        );
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        10L
                )
        ).thenReturn(true);

        // when
        WorkspaceDetailResult result = service.findDetail(
                1L,
                10L
        );

        // then
        assertThat(result.name()).isEqualTo("Knot 팀");
    }

    @Test
    @DisplayName("워크스페이스 ID가 양수가 아니면 조회를 거부한다")
    void findDetail_failure_invalidWorkspaceId() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                workspaceRepository,
                workspaceMemberRepository
        );

        // when
        ThrowingCallable action = () -> service.findDetail(
                0L,
                10L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        verifyNoInteractions(
                workspaceRepository,
                workspaceMemberRepository
        );
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스는 조회할 수 없다")
    void findDetail_failure_workspaceNotFound() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                workspaceRepository,
                workspaceMemberRepository
        );
        when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

        // when
        ThrowingCallable action = () -> service.findDetail(
                1L,
                10L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        verifyNoInteractions(workspaceMemberRepository);
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 워크스페이스 이름을 조회할 수 없다")
    void findDetail_failure_accessDenied() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                workspaceRepository,
                workspaceMemberRepository
        );
        when(workspaceRepository.findById(1L)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "Knot 팀",
                                CREATED_AT
                        )
                )
        );
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        10L
                )
        ).thenReturn(false);

        // when
        ThrowingCallable action = () -> service.findDetail(
                1L,
                10L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
    }

    @Test
    @DisplayName("인증된 멤버가 속한 워크스페이스를 최근 참여 순서로 조회한다")
    void findAllByMemberId_success() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                workspaceRepository,
                workspaceMemberRepository
        );
        Workspace recentWorkspace = workspace(
                2L,
                "최근 팀"
        );
        Workspace previousWorkspace = workspace(
                1L,
                "이전 팀"
        );
        when(workspaceRepository.findAllByMemberId(10L)).thenReturn(
                List.of(
                        recentWorkspace,
                        previousWorkspace
                )
        );
        when(workspaceMemberRepository.findLastViewedByMemberId(10L)).thenReturn(
                Optional.of(
                        WorkspaceMember.create(
                                2L,
                                10L,
                                WorkspaceMemberRole.MEMBER,
                                CREATED_AT
                        )
                )
        );

        // when
        WorkspaceListResult result = service.findAllByMemberId(10L);

        // then
        assertThat(result.lastViewedWorkspaceId()).isEqualTo(2L);
        assertThat(result.workspaces()).extracting(
                workspace -> workspace.id(),
                workspace -> workspace.name()
        )
                .containsExactly(
                        tuple(
                                2L,
                                "최근 팀"
                        ),
                        tuple(
                                1L,
                                "이전 팀"
                        )
                );
        verify(workspaceRepository).findAllByMemberId(10L);
        verify(workspaceMemberRepository).findLastViewedByMemberId(10L);
    }

    @Test
    @DisplayName("소속 워크스페이스가 없으면 빈 목록을 반환한다")
    void findAllByMemberId_success_emptyMemberships() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                workspaceRepository,
                workspaceMemberRepository
        );
        when(workspaceRepository.findAllByMemberId(10L)).thenReturn(List.of());
        when(workspaceMemberRepository.findLastViewedByMemberId(10L)).thenReturn(Optional.empty());

        // when
        WorkspaceListResult result = service.findAllByMemberId(10L);

        // then
        assertThat(result.lastViewedWorkspaceId()).isNull();
        assertThat(result.workspaces()).isEmpty();
        verify(workspaceRepository).findAllByMemberId(10L);
        verify(workspaceMemberRepository).findLastViewedByMemberId(10L);
    }

    @Test
    @DisplayName("마지막 조회 멤버십이 목록에 없으면 포인터를 노출하지 않는다")
    void findAllByMemberId_success_filtersStaleLastViewedWorkspace() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                workspaceRepository,
                workspaceMemberRepository
        );
        Workspace currentWorkspace = workspace(
                1L,
                "현재 팀"
        );
        when(workspaceRepository.findAllByMemberId(10L)).thenReturn(List.of(currentWorkspace));
        when(workspaceMemberRepository.findLastViewedByMemberId(10L)).thenReturn(
                Optional.of(
                        WorkspaceMember.create(
                                2L,
                                10L,
                                WorkspaceMemberRole.MEMBER,
                                CREATED_AT
                        )
                )
        );

        // when
        WorkspaceListResult result = service.findAllByMemberId(10L);

        // then
        assertThat(result.lastViewedWorkspaceId()).isNull();
        assertThat(result.workspaces()).extracting(workspace -> workspace.id())
                .containsExactly(1L);
    }

    private Workspace workspace(
            long workspaceId,
            String name
    ) {
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(workspaceId);
        when(workspace.getName()).thenReturn(name);
        return workspace;
    }
}
