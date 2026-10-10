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
import com.knot.backend.recording.application.dto.result.RecordingEndResult;
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
import org.mockito.InOrder;

class RecordingEndServiceTest {
    private static final Instant STARTED_AT = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-05T00:10:00.123456Z");
    private static final long WORKSPACE_ID = 10L;
    private static final long MEMBER_ID = 1L;
    private static final long RECORDING_ID = 7L;
    private static final UUID TAB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CONTROL_TOKEN = "A".repeat(43);
    private static final String OTHER_CONTROL_TOKEN = "B".repeat(43);
    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);

    private WorkspaceRepository workspaceRepository;
    private WorkspaceMemberRepository workspaceMemberRepository;
    private RecordingSessionRepository recordingSessionRepository;
    private RecordingControlTokenHasher hasher;
    private RecordingEndService service;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        recordingSessionRepository = mock(RecordingSessionRepository.class);
        hasher = mock(RecordingControlTokenHasher.class);
        when(hasher.hash(CONTROL_TOKEN)).thenReturn(HASH);
        when(hasher.hash(OTHER_CONTROL_TOKEN)).thenReturn(OTHER_HASH);
        service = new RecordingEndService(
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
    @DisplayName("Workspace를 먼저 잠근 뒤 최초 탭의 녹음을 잠그고 서버 시각으로 종료한다")
    void end_success_ownRecording() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        keepAlive(
                session,
                NOW
        );
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));
        when(recordingSessionRepository.save(session)).thenReturn(session);

        // when
        RecordingEndResult result = service.end(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID,
                command(TAB_ID)
        );

        // then
        assertThat(result.status()).isEqualTo(RecordingStatus.ENDED);
        assertThat(result.endedAt()).isEqualTo(NOW);
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(600_123L);
        InOrder inOrder = inOrder(
                workspaceRepository,
                workspaceMemberRepository,
                recordingSessionRepository
        );
        inOrder.verify(workspaceRepository)
                .findByIdForUpdate(WORKSPACE_ID);
        inOrder.verify(workspaceMemberRepository)
                .existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                );
        inOrder.verify(recordingSessionRepository)
                .findByIdForUpdate(RECORDING_ID);
        inOrder.verify(recordingSessionRepository)
                .save(session);
    }

    @Test
    @DisplayName("연결이 만료된 녹음을 종료하면 요청 시각이 아니라 만료 시각으로 종료한다")
    void end_success_connectionExpired() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));
        when(recordingSessionRepository.save(session)).thenReturn(session);

        // when
        RecordingEndResult result = service.end(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID,
                command(TAB_ID)
        );

        // then
        assertThat(result.status()).isEqualTo(RecordingStatus.ENDED);
        assertThat(result.endedAt()).isEqualTo(STARTED_AT.plusSeconds(120));
        assertThat(session.getEndReason()).isEqualTo(RecordingEndReason.CONNECTION_EXPIRED);
    }

    @Test
    @DisplayName("최초 탭이 이미 종료된 녹음을 다시 종료하면 처음 확정한 종료 시각을 그대로 반환한다")
    void end_success_alreadyEnded() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        Instant firstEndedAt = STARTED_AT.plusSeconds(60);
        session.end(firstEndedAt);
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));
        when(recordingSessionRepository.save(session)).thenReturn(session);

        // when
        RecordingEndResult result = service.end(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID,
                command(TAB_ID)
        );

        // then
        assertThat(result.status()).isEqualTo(RecordingStatus.ENDED);
        assertThat(result.endedAt()).isEqualTo(firstEndedAt);
    }

    @Test
    @DisplayName("다른 멤버의 녹음은 제어 증명을 확인하기 전에 거절하고 저장하지 않는다")
    void end_failure_notStarter() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID + 1);
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));

        // when
        Throwable failure = catchThrowable(
                () -> service.end(
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
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.RECORDING);
        verifyNoInteractions(hasher);
        verify(
                recordingSessionRepository,
                never()
        ).save(any(RecordingSession.class));
    }

    @Test
    @DisplayName("같은 회원이라도 다른 탭이면 종료를 거절하고 저장하지 않는다")
    void end_failure_otherTab() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));

        // when
        Throwable failure = catchThrowable(
                () -> service.end(
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
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(session.getEndedAt()).isNull();
        verify(
                recordingSessionRepository,
                never()
        ).save(any(RecordingSession.class));
    }

    @Test
    @DisplayName("최초 탭 ID라도 제어 증명이 다르면 종료를 거절하고 저장하지 않는다")
    void end_failure_wrongControlToken() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));

        // when
        Throwable failure = catchThrowable(
                () -> service.end(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        new RecordingControlCommand(
                                TAB_ID,
                                OTHER_CONTROL_TOKEN
                        )
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_CONTROL_DENIED);
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.RECORDING);
        verify(
                recordingSessionRepository,
                never()
        ).save(any(RecordingSession.class));
    }

    @Test
    @DisplayName("이미 종료된 녹음도 다른 탭의 재요청이면 종료 결과를 돌려주지 않고 거절한다")
    void end_failure_alreadyEndedFromOtherTab() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        Instant firstEndedAt = STARTED_AT.plusSeconds(60);
        session.end(firstEndedAt);
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));

        // when
        Throwable failure = catchThrowable(
                () -> service.end(
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
        assertThat(session.getEndedAt()).isEqualTo(firstEndedAt);
        verify(
                recordingSessionRepository,
                never()
        ).save(any(RecordingSession.class));
    }

    @Test
    @DisplayName("최초 탭이 폐기된 녹음을 종료하면 폐기 상태를 유지하고 거절한다")
    void end_failure_discardedRecording() {
        // given
        prepareAccess();
        RecordingSession session = recordingSession(MEMBER_ID);
        session.discard(STARTED_AT.plusSeconds(60));
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(session));

        // when
        Throwable failure = catchThrowable(
                () -> service.end(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        command(TAB_ID)
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_ALREADY_DISCARDED);
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.DISCARDED);
        verify(
                recordingSessionRepository,
                never()
        ).save(any(RecordingSession.class));
    }

    @Test
    @DisplayName("녹음이 없으면 녹음 없음으로 거절한다")
    void end_failure_recordingNotFound() {
        // given
        prepareAccess();
        when(recordingSessionRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.empty());

        // when
        Throwable failure = catchThrowable(
                () -> service.end(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        command(TAB_ID)
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_NOT_FOUND);
    }

    @Test
    @DisplayName("Workspace 멤버가 아니면 녹음을 조회하지 않고 거절한다")
    void end_failure_notWorkspaceMember() {
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
                () -> service.end(
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
    @DisplayName("양수가 아닌 녹음 ID는 녹음을 조회하지 않고 거절한다")
    void end_failure_invalidRecordingId() {
        // given
        prepareAccess();

        // when
        Throwable failure = catchThrowable(
                () -> service.end(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        0L,
                        command(TAB_ID)
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.INVALID_RECORDING_DATA);
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
