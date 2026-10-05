package com.knot.backend.recording.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.recording.application.dto.result.RecordingCurrentResult;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import com.knot.backend.recording.domain.RecordingStatus;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingCurrentServiceTest {
    private static final Instant STARTED_AT = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-05T00:10:00.123456Z");
    private static final long WORKSPACE_ID = 10L;
    private static final long MEMBER_ID = 1L;
    private static final long RECORDING_ID = 7L;
    private static final UUID TAB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String HASH = "a".repeat(64);

    private WorkspaceRepository workspaceRepository;
    private WorkspaceMemberRepository workspaceMemberRepository;
    private RecordingSessionRepository recordingSessionRepository;
    private RecordingCurrentService service;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        recordingSessionRepository = mock(RecordingSessionRepository.class);
        service = new RecordingCurrentService(
                new RecordingWorkspaceAccessValidator(
                        workspaceRepository,
                        workspaceMemberRepository
                ),
                recordingSessionRepository,
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    @DisplayName("진행 중인 본인 녹음은 서버 시각까지의 누적 시간과 함께 반환한다")
    void findCurrent_success_recording() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession();
        when(
                recordingSessionRepository.findActiveByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(Optional.of(session));

        // when
        Optional<RecordingCurrentResult> result = service.findCurrent(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).contains(
                new RecordingCurrentResult(
                        RECORDING_ID,
                        RecordingStatus.RECORDING,
                        STARTED_AT,
                        600_123L
                )
        );
    }

    @Test
    @DisplayName("일시정지한 녹음은 일시정지 이후 시간을 누적 시간에 더하지 않는다")
    void findCurrent_success_pausedExcludesPausedTime() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession();
        session.pause(STARTED_AT.plusSeconds(60));
        when(
                recordingSessionRepository.findActiveByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(Optional.of(session));

        // when
        Optional<RecordingCurrentResult> result = service.findCurrent(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).hasValueSatisfying(current -> {
            assertThat(current.status()).isEqualTo(RecordingStatus.PAUSED);
            assertThat(current.elapsedMillis()).isEqualTo(60_000L);
        });
    }

    @Test
    @DisplayName("요청 Workspace에 본인 활성 녹음이 없으면 빈 결과를 반환한다")
    void findCurrent_success_noActiveRecording() {
        // given
        prepareAccess();
        when(
                recordingSessionRepository.findActiveByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(Optional.empty());

        // when
        Optional<RecordingCurrentResult> result = service.findCurrent(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("조회는 Workspace를 잠그지 않고 녹음 세션을 저장하지 않는다")
    void findCurrent_success_readsWithoutLockOrSave() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession();
        when(
                recordingSessionRepository.findActiveByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(Optional.of(session));

        // when
        service.findCurrent(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        verify(
                workspaceRepository,
                never()
        ).findByIdForUpdate(WORKSPACE_ID);
        verify(
                recordingSessionRepository,
                never()
        ).save(session);
        assertThat(session.getLastSeenAt()).isEqualTo(STARTED_AT);
    }

    @Test
    @DisplayName("Workspace 멤버가 아니면 녹음을 조회하지 않고 거절한다")
    void findCurrent_failure_notWorkspaceMember() {
        // given
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(false);

        // when
        Throwable failure = catchThrowable(
                () -> service.findCurrent(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        );

        // then
        assertThat(failure).isInstanceOf(WorkspaceException.class)
                .extracting("errorCode")
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        verifyNoInteractions(recordingSessionRepository);
    }

    @Test
    @DisplayName("없거나 삭제된 Workspace면 WORKSPACE_NOT_FOUND로 거절한다")
    void findCurrent_failure_workspaceNotFound() {
        // given
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.empty());

        // when
        Throwable failure = catchThrowable(
                () -> service.findCurrent(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        );

        // then
        assertThat(failure).isInstanceOf(WorkspaceException.class)
                .extracting("errorCode")
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        verifyNoInteractions(recordingSessionRepository);
    }

    @Test
    @DisplayName("0 이하 Workspace ID는 저장소를 조회하지 않고 INVALID_WORKSPACE_ID로 거절한다")
    void findCurrent_failure_invalidWorkspaceId() {
        // given
        long invalidWorkspaceId = 0L;

        // when
        Throwable failure = catchThrowable(
                () -> service.findCurrent(
                        invalidWorkspaceId,
                        MEMBER_ID
                )
        );

        // then
        assertThat(failure).isInstanceOf(WorkspaceException.class)
                .extracting("errorCode")
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        verifyNoInteractions(workspaceRepository);
        verify(
                recordingSessionRepository,
                never()
        ).findActiveByWorkspaceIdAndMemberId(
                anyLong(),
                anyLong()
        );
    }

    private void prepareAccess() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(true);
    }

    private Workspace workspace() {
        return Workspace.create(
                "Knot 팀",
                STARTED_AT
        );
    }

    private RecordingSession recordingSession() {
        RecordingSession session = spy(
                RecordingSession.start(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        UUID.randomUUID(),
                        TAB_ID,
                        HASH,
                        STARTED_AT
                )
        );
        doReturn(RECORDING_ID).when(session)
                .getId();
        return session;
    }
}
