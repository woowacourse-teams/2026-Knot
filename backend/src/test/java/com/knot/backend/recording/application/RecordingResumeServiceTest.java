package com.knot.backend.recording.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.recording.application.dto.command.RecordingControlCommand;
import com.knot.backend.recording.application.dto.result.RecordingResumeResult;
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
import org.mockito.InOrder;

class RecordingResumeServiceTest {
    private static final Instant STARTED_AT = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-05T00:10:00.123456Z");
    private static final long WORKSPACE_ID = 10L;
    private static final long MEMBER_ID = 1L;
    private static final long RECORDING_ID = 7L;
    private static final UUID TAB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CONTROL_TOKEN = "A".repeat(43);
    private static final String HASH = "a".repeat(64);

    private WorkspaceRepository workspaceRepository;
    private WorkspaceMemberRepository workspaceMemberRepository;
    private RecordingSessionRepository recordingSessionRepository;
    private RecordingControlTokenHasher hasher;
    private RecordingResumeService service;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        recordingSessionRepository = mock(RecordingSessionRepository.class);
        hasher = mock(RecordingControlTokenHasher.class);
        when(hasher.hash(CONTROL_TOKEN)).thenReturn(HASH);
        service = new RecordingResumeService(
                new RecordingResumeTransaction(
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
                )
        );
    }

    @Test
    @DisplayName("Workspace를 잠근 뒤 최초 탭의 일시정지된 녹음을 잠그고 서버 시각으로 재개한다")
    void resume_success_firstTab() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        session.pause(STARTED_AT.plusSeconds(60));
        keepAlive(
                session,
                NOW
        );
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));
        when(recordingSessionRepository.save(session)).thenReturn(session);

        // when
        RecordingResumeResult result = service.resume(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID,
                command(TAB_ID)
        );

        // then
        assertThat(result.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(result.resumedAt()).isEqualTo(NOW);
        assertThat(result.elapsedMillis()).isEqualTo(60_000L);
        InOrder inOrder = inOrder(
                workspaceRepository,
                recordingSessionRepository
        );
        inOrder.verify(workspaceRepository)
                .findByIdForUpdate(WORKSPACE_ID);
        inOrder.verify(recordingSessionRepository)
                .findByIdForUpdate(RECORDING_ID);
        inOrder.verify(recordingSessionRepository)
                .save(session);
    }

    @Test
    @DisplayName("같은 회원이라도 다른 탭이면 재개를 거절하고 저장하지 않는다")
    void resume_failure_otherTab() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        session.pause(STARTED_AT.plusSeconds(60));
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));

        // when
        Throwable failure = catchThrowable(
                () -> service.resume(
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
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.PAUSED);
        verify(
                recordingSessionRepository,
                never()
        ).save(any(RecordingSession.class));
    }

    @Test
    @DisplayName("다른 멤버의 녹음은 제어 증명을 확인하기 전에 거절한다")
    void resume_failure_notStarter() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID + 1);
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));

        // when
        Throwable failure = catchThrowable(
                () -> service.resume(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        command(TAB_ID)
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_CONTROL_DENIED);
        verifyNoInteractions(hasher);
    }

    @Test
    @DisplayName("종료된 녹음은 재개로 되살리지 않고 거절한다")
    void resume_failure_alreadyEnded() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        session.end(STARTED_AT.plusSeconds(60));
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));

        // when
        Throwable failure = catchThrowable(
                () -> service.resume(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        command(TAB_ID)
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_ALREADY_ENDED);
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.ENDED);
    }

    @Test
    @DisplayName("Workspace 멤버가 아니면 녹음을 조회하지 않고 거절한다")
    void resume_failure_notWorkspaceMember() {
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
                () -> service.resume(
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

    @Test
    @DisplayName("일시정지 중 연결이 만료된 녹음은 만료 종료를 저장한 뒤 이미 종료된 녹음으로 거절한다")
    void resume_failure_connectionExpired() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        session.pause(STARTED_AT.plusSeconds(60));
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));
        when(recordingSessionRepository.save(session)).thenReturn(session);

        // when
        Throwable failure = catchThrowable(
                () -> service.resume(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        command(TAB_ID)
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_ALREADY_ENDED);
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.ENDED);
        assertThat(session.getEndedAt()).isEqualTo(STARTED_AT.plusSeconds(180));
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(60_000L);
        verify(recordingSessionRepository).save(session);
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

    private RecordingSession recordingSession(long memberId) {
        RecordingSession session = spy(newRecordingSession(memberId));
        doReturn(RECORDING_ID).when(session)
                .getId();
        return session;
    }

    private RecordingSession newRecordingSession(long memberId) {
        return RecordingSession.start(
                WORKSPACE_ID,
                memberId,
                UUID.randomUUID(),
                TAB_ID,
                HASH,
                STARTED_AT
        );
    }

    private void keepAlive(
            RecordingSession session,
            Instant until
    ) {
        for (Instant at = session.getLastSeenAt()
                .plusSeconds(60); at.isBefore(until); at = at.plusSeconds(60)) {
            session.recordHeartbeat(at);
        }
    }
}
