package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.WorkspaceCreateResult;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkspaceServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    @DisplayName("워크스페이스를 생성하면 생성자를 OWNER 멤버십으로 저장하고 ID를 반환한다")
    void create_success() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceService service = service(
                workspaceRepository,
                workspaceMemberRepository
        );
        Workspace savedWorkspace = mock(Workspace.class);
        when(savedWorkspace.getId()).thenReturn(7L);
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(savedWorkspace);

        // when
        WorkspaceCreateResult result = service.create(
                3L,
                "Knot 팀"
        );

        // then
        ArgumentCaptor<Workspace> workspaceCaptor = ArgumentCaptor.forClass(Workspace.class);
        ArgumentCaptor<WorkspaceMember> memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceRepository).save(workspaceCaptor.capture());
        verify(workspaceMemberRepository).save(memberCaptor.capture());
        assertThat(result.id()).isEqualTo(7L);
        assertThat(workspaceCaptor.getValue()).extracting(
                Workspace::getName,
                Workspace::getCreatedAt
        )
                .containsExactly(
                        "Knot 팀",
                        CREATED_AT
                );
        assertThat(memberCaptor.getValue()).extracting(
                WorkspaceMember::getWorkspaceId,
                WorkspaceMember::getMemberId,
                WorkspaceMember::getRole,
                WorkspaceMember::getJoinedAt
        )
                .containsExactly(
                        7L,
                        3L,
                        WorkspaceMemberRole.OWNER,
                        CREATED_AT
                );
    }

    @Test
    @DisplayName("워크스페이스 이름이 올바르지 않으면 아무것도 저장하지 않는다")
    void create_failure_invalidWorkspaceName() {
        // given
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceService service = service(
                workspaceRepository,
                workspaceMemberRepository
        );

        // when
        Throwable thrown = catchThrowable(
                () -> service.create(
                        3L,
                        "Knot!"
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                WorkspaceException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_NAME)
        );
        verify(
                workspaceRepository,
                never()
        ).save(any(Workspace.class));
        verify(
                workspaceMemberRepository,
                never()
        ).save(any(WorkspaceMember.class));
    }

    private WorkspaceService service(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository
    ) {
        return new WorkspaceService(
                workspaceRepository,
                workspaceMemberRepository,
                Clock.fixed(
                        CREATED_AT,
                        ZoneOffset.UTC
                )
        );
    }
}
