package com.knot.backend.recording.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.recording.application.dto.result.RecordingDetailResult;
import com.knot.backend.recording.application.dto.result.RecordingDetailSnapshot;
import com.knot.backend.recording.domain.RecordingAudioUploadStatus;
import com.knot.backend.recording.domain.RecordingEndReason;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingDetailServiceTest {
    private static final Instant STARTED_AT = Instant.parse("2026-10-09T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-09T00:01:30.123456Z");
    private static final long WORKSPACE_ID = 10L;
    private static final long MEMBER_ID = 1L;
    private static final long RECORDING_ID = 7L;

    private WorkspaceRepository workspaceRepository;
    private WorkspaceMemberRepository workspaceMemberRepository;
    private RecordingDetailQuery recordingDetailQuery;
    private RecordingDetailService service;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        recordingDetailQuery = mock(RecordingDetailQuery.class);
        service = new RecordingDetailService(
                new RecordingWorkspaceAccessValidator(
                        workspaceRepository,
                        workspaceMemberRepository
                ),
                recordingDetailQuery,
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    @DisplayName("진행 중인 본인 녹음은 서버 시각까지의 경과 시간과 연결 만료 예정 시각, 최대 녹음 시간을 반환한다")
    void find_success_recording() {
        // given
        prepareAccess();
        when(
                recordingDetailQuery.find(
                        WORKSPACE_ID,
                        RECORDING_ID
                )
        ).thenReturn(
                Optional.of(
                        snapshot(
                                RecordingStatus.RECORDING,
                                NOW.minusSeconds(30),
                                null,
                                null,
                                null
                        )
                )
        );

        // when
        RecordingDetailResult result = service.find(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID
        );

        // then
        assertThat(result.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(result.elapsedMillis()).isEqualTo(90_000L);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(90));
        assertThat(result.serverNow()).isEqualTo(NOW);
        assertThat(result.maxDurationMillis()).isEqualTo(7_200_000L);
        assertThat(result.audioUploadStatus()).isNull();
        assertThat(result.uploadId()).isNull();
        assertThat(result.completedAt()).isNull();
    }

    @Test
    @DisplayName("종료되고 업로드가 끝난 녹음은 종료 시각·사유와 업로드 완료 정보를 반환하고 만료 예정 시각은 비운다")
    void find_success_endedWithCompletedUpload() {
        // given
        prepareAccess();
        Instant endedAt = STARTED_AT.plusSeconds(60);
        when(
                recordingDetailQuery.find(
                        WORKSPACE_ID,
                        RECORDING_ID
                )
        ).thenReturn(
                Optional.of(
                        snapshot(
                                RecordingStatus.ENDED,
                                null,
                                endedAt,
                                RecordingAudioUploadStatus.COMPLETED,
                                NOW
                        )
                )
        );

        // when
        RecordingDetailResult result = service.find(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID
        );

        // then
        assertThat(result.status()).isEqualTo(RecordingStatus.ENDED);
        assertThat(result.endedAt()).isEqualTo(endedAt);
        assertThat(result.endReason()).isEqualTo(RecordingEndReason.USER_ENDED);
        assertThat(result.elapsedMillis()).isEqualTo(60_000L);
        assertThat(result.expiresAt()).isNull();
        assertThat(result.audioUploadStatus()).isEqualTo(RecordingAudioUploadStatus.COMPLETED);
        assertThat(result.uploadId()).isEqualTo(3L);
        assertThat(result.completedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("진행 구간 시작이 서버 시각보다 늦게 기록돼도 경과 시간을 음수로 세지 않는다")
    void find_success_doesNotCountNegativeInterval() {
        // given
        prepareAccess();
        when(
                recordingDetailQuery.find(
                        WORKSPACE_ID,
                        RECORDING_ID
                )
        ).thenReturn(
                Optional.of(
                        snapshot(
                                RecordingStatus.RECORDING,
                                NOW.plusMillis(5),
                                null,
                                null,
                                null
                        )
                )
        );

        // when
        RecordingDetailResult result = service.find(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID
        );

        // then
        assertThat(result.elapsedMillis()).isEqualTo(60_000L);
    }

    @Test
    @DisplayName("다른 회원의 녹음은 시작자가 아니라는 이유로 거절한다")
    void find_failure_otherMember() {
        // given
        prepareAccess();
        when(
                recordingDetailQuery.find(
                        WORKSPACE_ID,
                        RECORDING_ID
                )
        ).thenReturn(
                Optional.of(
                        new RecordingDetailSnapshot(
                                RECORDING_ID,
                                MEMBER_ID + 1,
                                RecordingStatus.RECORDING,
                                STARTED_AT,
                                STARTED_AT,
                                0L,
                                STARTED_AT,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                )
        );

        // when
        Throwable thrown = catchThrowable(
                () -> service.find(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID
                )
        );

        // then
        assertThat(thrown).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_CONTROL_DENIED);
    }

    @Test
    @DisplayName("요청한 Workspace에 없는 녹음은 녹음 없음으로 거절한다")
    void find_failure_notFound() {
        // given
        prepareAccess();
        when(
                recordingDetailQuery.find(
                        WORKSPACE_ID,
                        RECORDING_ID
                )
        ).thenReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(
                () -> service.find(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID
                )
        );

        // then
        assertThat(thrown).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_NOT_FOUND);
    }

    @Test
    @DisplayName("Workspace 멤버가 아니면 녹음을 조회하지 않고 거절한다")
    void find_failure_notWorkspaceMember() {
        // given
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(false);

        // when
        Throwable thrown = catchThrowable(
                () -> service.find(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID
                )
        );

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting("errorCode")
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        verifyNoInteractions(recordingDetailQuery);
    }

    @Test
    @DisplayName("양수가 아닌 녹음 ID는 녹음을 조회하지 않고 거절한다")
    void find_failure_invalidRecordingId() {
        // given
        prepareAccess();

        // when
        Throwable thrown = catchThrowable(
                () -> service.find(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        0L
                )
        );

        // then
        assertThat(thrown).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.INVALID_RECORDING_DATA);
        verifyNoInteractions(recordingDetailQuery);
    }

    private RecordingDetailSnapshot snapshot(
            RecordingStatus status,
            Instant currentIntervalStartedAt,
            Instant endedAt,
            RecordingAudioUploadStatus uploadStatus,
            Instant completedAt
    ) {
        return new RecordingDetailSnapshot(
                RECORDING_ID,
                MEMBER_ID,
                status,
                STARTED_AT,
                currentIntervalStartedAt,
                60_000L,
                currentIntervalStartedAt == null ? STARTED_AT : currentIntervalStartedAt,
                endedAt,
                endedAt == null ? null : RecordingEndReason.USER_ENDED,
                uploadStatus == null ? null : 3L,
                uploadStatus,
                completedAt
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
}
