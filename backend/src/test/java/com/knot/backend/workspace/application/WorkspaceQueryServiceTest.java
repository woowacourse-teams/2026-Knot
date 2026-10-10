package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.WorkspaceDetailResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceDetailSnapshot;
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
    @DisplayName("워크스페이스 멤버는 이름과 내 역할, 활성 멤버 수를 조회한다")
    void findDetail_success() {
        // given
        WorkspaceDetailQuery workspaceDetailQuery = mock(WorkspaceDetailQuery.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                mock(WorkspaceRepository.class),
                mock(WorkspaceMemberRepository.class),
                workspaceDetailQuery
        );
        when(
                workspaceDetailQuery.find(
                        1L,
                        10L
                )
        ).thenReturn(
                Optional.of(
                        new WorkspaceDetailSnapshot(
                                "Knot 팀",
                                WorkspaceMemberRole.OWNER,
                                2L
                        )
                )
        );

        // when
        WorkspaceDetailResult result = service.findDetail(
                1L,
                10L
        );

        // then
        assertThat(result.name()).isEqualTo("Knot 팀");
        assertThat(result.myRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(result.activeMemberCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("워크스페이스 ID가 양수가 아니면 조회를 거부한다")
    void findDetail_failure_invalidWorkspaceId() {
        // given
        WorkspaceDetailQuery workspaceDetailQuery = mock(WorkspaceDetailQuery.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                mock(WorkspaceRepository.class),
                mock(WorkspaceMemberRepository.class),
                workspaceDetailQuery
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
        verifyNoInteractions(workspaceDetailQuery);
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 워크스페이스는 조회할 수 없다")
    void findDetail_failure_workspaceNotFound() {
        // given
        WorkspaceDetailQuery workspaceDetailQuery = mock(WorkspaceDetailQuery.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                mock(WorkspaceRepository.class),
                mock(WorkspaceMemberRepository.class),
                workspaceDetailQuery
        );
        when(
                workspaceDetailQuery.find(
                        1L,
                        10L
                )
        ).thenReturn(Optional.empty());

        // when
        ThrowingCallable action = () -> service.findDetail(
                1L,
                10L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
    }

    @Test
    @DisplayName("현재 멤버십이 없으면 워크스페이스 정보를 조회할 수 없다")
    void findDetail_failure_accessDenied() {
        // given
        WorkspaceDetailQuery workspaceDetailQuery = mock(WorkspaceDetailQuery.class);
        WorkspaceQueryService service = new WorkspaceQueryService(
                mock(WorkspaceRepository.class),
                mock(WorkspaceMemberRepository.class),
                workspaceDetailQuery
        );
        when(
                workspaceDetailQuery.find(
                        1L,
                        10L
                )
        ).thenReturn(
                Optional.of(
                        new WorkspaceDetailSnapshot(
                                "Knot 팀",
                                null,
                                1L
                        )
                )
        );

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
                workspaceMemberRepository,
                mock(WorkspaceDetailQuery.class)
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
                workspaceMemberRepository,
                mock(WorkspaceDetailQuery.class)
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
                workspaceMemberRepository,
                mock(WorkspaceDetailQuery.class)
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
