package com.knot.backend.recording.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.recording.application.dto.command.RecordingControlCommand;
import com.knot.backend.recording.application.dto.result.RecordingHeartbeatResult;
import com.knot.backend.recording.domain.RecordingEndReason;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
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

class RecordingHeartbeatServiceTest {
    private static final Instant STARTED_AT = Instant.parse("2026-10-09T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-09T00:01:30.123456Z");
    private static final long WORKSPACE_ID = 10L;
    private static final long MEMBER_ID = 1L;
    private static final long RECORDING_ID = 7L;
    private static final UUID TAB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CONTROL_TOKEN = "A".repeat(43);
    private static final String HASH = "a".repeat(64);

    private WorkspaceRepository workspaceRepository;
    private WorkspaceMemberRepository workspaceMemberRepository;
    private RecordingSessionRepository recordingSessionRepository;
    private RecordingHeartbeatService service;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        recordingSessionRepository = mock(RecordingSessionRepository.class);
        RecordingControlTokenHasher hasher = mock(RecordingControlTokenHasher.class);
        when(hasher.hash(CONTROL_TOKEN)).thenReturn(HASH);
        service = new RecordingHeartbeatService(
                new RecordingControlledSessionLoader(
                        new RecordingWorkspaceAccessValidator(
                                workspaceRepository,
                                workspaceMemberRepository
                        ),
                        recordingSessionRepository,
                        hasher
                ),
                recordingSessionRepository,
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    @DisplayName("최초 탭의 신호는 마지막 신호 시각을 서버 시각으로 갱신하고 다음 만료 시각을 돌려준다")
    void heartbeat_success_firstTab() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(STARTED_AT);
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));
        when(recordingSessionRepository.save(session)).thenReturn(session);

        // when
        RecordingHeartbeatResult result = service.heartbeat(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID,
                command(TAB_ID)
        );

        // then
        assertThat(result.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(result.lastSeenAt()).isEqualTo(NOW);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(result.elapsedMillis()).isEqualTo(90_123L);
        assertThat(result.endedAt()).isNull();
        assertThat(result.endReason()).isNull();
        assertThat(result.serverNow()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("마지막 신호 후 120초가 지난 신호는 녹음을 되살리지 않고 연결 만료 종료 상태를 돌려준다")
    void heartbeat_success_expiredReturnsEndedSnapshot() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(NOW.minusSeconds(120));
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));
        when(recordingSessionRepository.save(session)).thenReturn(session);

        // when
        RecordingHeartbeatResult result = service.heartbeat(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID,
                command(TAB_ID)
        );

        // then
        assertThat(result.status()).isEqualTo(RecordingStatus.ENDED);
        assertThat(result.endReason()).isEqualTo(RecordingEndReason.CONNECTION_EXPIRED);
        assertThat(result.endedAt()).isEqualTo(NOW);
        assertThat(result.lastSeenAt()).isEqualTo(NOW.minusSeconds(120));
        assertThat(result.expiresAt()).isNull();
        verify(recordingSessionRepository).save(session);
    }

    @Test
    @DisplayName("같은 회원이라도 다른 탭의 신호는 거절하고 마지막 신호 시각을 바꾸지 않는다")
    void heartbeat_failure_otherTab() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(STARTED_AT);
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));

        // when
        Throwable failure = catchThrowable(
                () -> service.heartbeat(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        command(UUID.randomUUID())
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_CONTROL_DENIED);
        assertThat(session.getLastSeenAt()).isEqualTo(STARTED_AT);
        verify(
                recordingSessionRepository,
                never()
        ).save(any(RecordingSession.class));
    }

    @Test
    @DisplayName("Workspace 멤버가 아니면 녹음을 조회하지 않아 종료 상태도 공개하지 않는다")
    void heartbeat_failure_notWorkspaceMember() {
        // given
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(false);

        // when
        Throwable failure = catchThrowable(
                () -> service.heartbeat(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        command(TAB_ID)
                )
        );

        // then
        assertThat(failure).isInstanceOf(WorkspaceException.class)
                .extracting("errorCode")
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        verifyNoInteractions(recordingSessionRepository);
    }

    private void prepareAccess() {
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(true);
    }

    private RecordingControlCommand command(UUID tabId) {
        return new RecordingControlCommand(
                tabId,
                CONTROL_TOKEN
        );
    }

    private Workspace workspace() {
        return Workspace.create(
                "Knot 팀",
                STARTED_AT
        );
    }

    private RecordingSession recordingSession(Instant startedAt) {
        RecordingSession session = spy(
                RecordingSession.start(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        UUID.randomUUID(),
                        TAB_ID,
                        HASH,
                        startedAt
                )
        );
        doReturn(RECORDING_ID).when(session)
                .getId();
        return session;
    }
}
