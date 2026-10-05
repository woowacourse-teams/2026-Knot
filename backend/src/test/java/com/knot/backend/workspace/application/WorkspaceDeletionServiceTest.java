package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import com.knot.backend.recording.domain.RecordingStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class WorkspaceDeletionServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-10-05T00:01:00.123456Z");
    private static final long WORKSPACE_ID = 1L;
    private static final long OWNER_ID = 2L;
    private static final long MEMBER_ID = 3L;

    private WorkspaceRepository workspaceRepository;
    private WorkspaceMemberRepository workspaceMemberRepository;
    private RecordingSessionRepository recordingSessionRepository;
    private WorkspaceDeletionService service;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        recordingSessionRepository = mock(RecordingSessionRepository.class);
        service = new WorkspaceDeletionService(
                workspaceRepository,
                workspaceMemberRepository,
                recordingSessionRepository,
                Clock.fixed(
                        DELETED_AT,
                        ZoneOffset.UTC
                )
        );
    }

    @DisplayName("OWNER가 삭제하면 워크스페이스를 논리 삭제하고 모든 활성 멤버를 내보내며 진행 중 녹음을 폐기한다")
    @Test
    void delete_success_owner() {
        // given
        Workspace workspace = workspace();
        WorkspaceMember owner = workspaceMember(
                OWNER_ID,
                WorkspaceMemberRole.OWNER
        );
        WorkspaceMember member = workspaceMember(
                MEMBER_ID,
                WorkspaceMemberRole.MEMBER
        );
        member.markLastViewed();
        List<WorkspaceMember> activeMembers = List.of(
                owner,
                member
        );
        RecordingSession recordingSession = recordingSession(MEMBER_ID);
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findAllActiveByWorkspaceIdForUpdate(WORKSPACE_ID)).thenReturn(activeMembers);
        when(recordingSessionRepository.findAllActiveByWorkspaceIdForUpdate(WORKSPACE_ID))
                .thenReturn(List.of(recordingSession));

        // when
        service.delete(
                OWNER_ID,
                WORKSPACE_ID
        );

        // then
        assertThat(workspace.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(owner.getLeftAt()).isEqualTo(DELETED_AT);
        assertThat(member.getLeftAt()).isEqualTo(DELETED_AT);
        assertThat(member.isLastViewed()).isFalse();
        assertThat(recordingSession.getStatus()).isEqualTo(RecordingStatus.DISCARDED);
        InOrder inOrder = inOrder(
                workspaceRepository,
                workspaceMemberRepository,
                recordingSessionRepository
        );
        inOrder.verify(workspaceRepository)
                .findByIdForUpdate(WORKSPACE_ID);
        inOrder.verify(workspaceMemberRepository)
                .findAllActiveByWorkspaceIdForUpdate(WORKSPACE_ID);
        inOrder.verify(workspaceRepository)
                .save(workspace);
        inOrder.verify(workspaceMemberRepository)
                .saveAll(activeMembers);
        inOrder.verify(recordingSessionRepository)
                .findAllActiveByWorkspaceIdForUpdate(WORKSPACE_ID);
        inOrder.verify(recordingSessionRepository)
                .save(recordingSession);
    }

    @DisplayName("MEMBER가 삭제하면 OWNER 권한 필요로 거절하고 아무것도 변경하지 않는다")
    @Test
    void delete_failure_member() {
        // given
        Workspace workspace = workspace();
        WorkspaceMember owner = workspaceMember(
                OWNER_ID,
                WorkspaceMemberRole.OWNER
        );
        WorkspaceMember member = workspaceMember(
                MEMBER_ID,
                WorkspaceMemberRole.MEMBER
        );
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findAllActiveByWorkspaceIdForUpdate(WORKSPACE_ID)).thenReturn(
                List.of(
                        owner,
                        member
                )
        );

        // when
        ThrowingCallable action = () -> service.delete(
                MEMBER_ID,
                WORKSPACE_ID
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        assertThat(workspace.isDeleted()).isFalse();
        assertThat(owner.isActive()).isTrue();
        assertThat(member.isActive()).isTrue();
        verifyNoWrites();
    }

    @DisplayName("활성 멤버가 아니면 워크스페이스 접근 불가로 거절하고 아무것도 변경하지 않는다")
    @Test
    void delete_failure_notActiveMember() {
        // given
        Workspace workspace = workspace();
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findAllActiveByWorkspaceIdForUpdate(WORKSPACE_ID)).thenReturn(
                List.of(
                        workspaceMember(
                                OWNER_ID,
                                WorkspaceMemberRole.OWNER
                        )
                )
        );

        // when
        ThrowingCallable action = () -> service.delete(
                MEMBER_ID,
                WORKSPACE_ID
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        assertThat(workspace.isDeleted()).isFalse();
        verifyNoWrites();
    }

    @DisplayName("없거나 이미 삭제된 워크스페이스는 찾을 수 없음으로 거절한다")
    @Test
    void delete_failure_workspaceNotFound() {
        // given
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.empty());

        // when
        ThrowingCallable action = () -> service.delete(
                OWNER_ID,
                WORKSPACE_ID
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        verifyNoInteractions(
                workspaceMemberRepository,
                recordingSessionRepository
        );
    }

    @DisplayName("양수가 아닌 워크스페이스 ID는 조회 없이 거절한다")
    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void delete_failure_invalidWorkspaceId(Long workspaceId) {
        // given
        long memberId = OWNER_ID;

        // when
        ThrowingCallable action = () -> service.delete(
                memberId,
                workspaceId
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        verifyNoInteractions(
                workspaceRepository,
                workspaceMemberRepository,
                recordingSessionRepository
        );
    }

    private void verifyNoWrites() {
        verify(
                workspaceRepository,
                never()
        ).save(any(Workspace.class));
        verify(
                workspaceMemberRepository,
                never()
        ).saveAll(anyList());
        verifyNoInteractions(recordingSessionRepository);
    }

    private Workspace workspace() {
        return Workspace.create(
                "Knot 팀",
                CREATED_AT
        );
    }

    private WorkspaceMember workspaceMember(
            long memberId,
            WorkspaceMemberRole role
    ) {
        return WorkspaceMember.create(
                WORKSPACE_ID,
                memberId,
                role,
                CREATED_AT
        );
    }

    private RecordingSession recordingSession(long memberId) {
        return RecordingSession.start(
                WORKSPACE_ID,
                memberId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "a".repeat(64),
                CREATED_AT
        );
    }
}
