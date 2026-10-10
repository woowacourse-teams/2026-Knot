package com.knot.backend.recording.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingSessionTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TAB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CONTROL_TOKEN_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant STARTED_AT = Instant.parse("2026-10-01T00:00:00Z");

    @DisplayName("녹음을 시작하면 RECORDING 상태와 시작 시각을 저장한다")
    @Test
    void start_success() {
        // given

        // when
        RecordingSession recordingSession = startRecording();

        // then
        assertThat(recordingSession.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(recordingSession.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(recordingSession.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(recordingSession.getTabId()).isEqualTo(TAB_ID);
        assertThat(recordingSession.getStatus()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(recordingSession.getStartedAt()).isEqualTo(STARTED_AT);
        assertThat(recordingSession.getLastSeenAt()).isEqualTo(STARTED_AT);
        assertThat(recordingSession.getAccumulatedRecordingMillis()).isZero();
    }

    @DisplayName("동일한 시작 요청이면 true를 반환한다")
    @Test
    void matchesStart_success_sameRequest() {
        // given
        RecordingSession recordingSession = startRecording();

        // when
        boolean matches = recordingSession.matchesStart(
                WORKSPACE_ID,
                TAB_ID,
                CONTROL_TOKEN_HASH
        );

        // then
        assertThat(matches).isTrue();
    }

    @DisplayName("제어 토큰 해시가 다르면 동일한 시작 요청이 아니다")
    @Test
    void matchesStart_success_differentHash() {
        // given
        RecordingSession recordingSession = startRecording();

        // when
        boolean matches = recordingSession.matchesStart(
                WORKSPACE_ID,
                TAB_ID,
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        );

        // then
        assertThat(matches).isFalse();
    }

    @DisplayName("일시정지하면 현재 녹음 구간을 누적하고 PAUSED 상태가 된다")
    @Test
    void pause_success() {
        // given
        RecordingSession recordingSession = startRecording();

        // when
        recordingSession.pause(Instant.parse("2026-10-01T00:00:10Z"));

        // then
        assertThat(recordingSession.getStatus()).isEqualTo(RecordingStatus.PAUSED);
        assertThat(recordingSession.getAccumulatedRecordingMillis()).isEqualTo(10_000L);
        assertThat(recordingSession.getRecordingDurationMillis(Instant.parse("2026-10-01T00:01:00Z")))
                .isEqualTo(10_000L);
    }

    @DisplayName("일시정지 상태에서 다시 녹음하면 기존 누적 시간을 보존하고 새 구간을 시작한다")
    @Test
    void resume_success() {
        // given
        RecordingSession recordingSession = startRecording();
        recordingSession.pause(Instant.parse("2026-10-01T00:00:10Z"));

        // when
        recordingSession.resume(Instant.parse("2026-10-01T00:00:30Z"));

        // then
        assertThat(recordingSession.getStatus()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(recordingSession.getRecordingDurationMillis(Instant.parse("2026-10-01T00:00:35Z")))
                .isEqualTo(15_000L);
    }

    @DisplayName("녹음을 종료하면 누적 시간과 종료 시각을 고정한다")
    @Test
    void end_success() {
        // given
        RecordingSession recordingSession = startRecording();
        recordingSession.pause(Instant.parse("2026-10-01T00:00:10Z"));
        recordingSession.resume(Instant.parse("2026-10-01T00:00:20Z"));

        // when
        recordingSession.end(Instant.parse("2026-10-01T00:00:25Z"));

        // then
        assertThat(recordingSession.getStatus()).isEqualTo(RecordingStatus.ENDED);
        assertThat(recordingSession.getEndedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:25Z"));
        assertThat(recordingSession.getAccumulatedRecordingMillis()).isEqualTo(15_000L);
    }

    @DisplayName("종료를 반복하면 기존 종료 시각과 누적 시간을 보존한다")
    @Test
    void end_success_repeated() {
        // given
        RecordingSession recordingSession = startRecording();
        recordingSession.end(Instant.parse("2026-10-01T00:00:10Z"));

        // when
        recordingSession.end(Instant.parse("2026-09-30T23:59:00Z"));

        // then
        assertThat(recordingSession.getEndedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:10Z"));
        assertThat(recordingSession.getAccumulatedRecordingMillis()).isEqualTo(10_000L);
    }

    @DisplayName("종료된 녹음은 일시정지할 수 없다")
    @Test
    void pause_failure_alreadyEnded() {
        // given
        RecordingSession recordingSession = startRecording();
        recordingSession.end(Instant.parse("2026-10-01T00:00:10Z"));

        // when
        Throwable thrown = catchThrowable(() -> recordingSession.pause(Instant.parse("2026-10-01T00:00:11Z")));

        // then
        assertThat(thrown).isInstanceOf(RecordingException.class)
                .extracting(exception -> ((RecordingException) exception).getErrorCode())
                .isEqualTo(RecordingErrorCode.RECORDING_ALREADY_ENDED);
    }

    @DisplayName("현재 녹음 구간보다 과거 시각으로 누적 시간을 계산할 수 없다")
    @Test
    void getRecordingDurationMillis_failure_beforeCurrentInterval() {
        // given
        RecordingSession recordingSession = startRecording();

        // when
        Throwable thrown = catchThrowable(
                () -> recordingSession.getRecordingDurationMillis(Instant.parse("2026-09-30T23:59:59Z"))
        );

        // then
        assertThat(thrown).isInstanceOf(RecordingException.class)
                .extracting(exception -> ((RecordingException) exception).getErrorCode())
                .isEqualTo(RecordingErrorCode.INVALID_RECORDING_TIME);
    }

    @DisplayName("해시 형식이 올바르지 않으면 생성을 거부한다")
    @Test
    void start_failure_invalidHash() {
        // given
        String invalidControlTokenHash = "invalid";

        // when
        Throwable thrown = catchThrowable(
                () -> RecordingSession.start(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        REQUEST_ID,
                        TAB_ID,
                        invalidControlTokenHash,
                        STARTED_AT
                )
        );

        // then
        assertThat(thrown).isInstanceOf(RecordingException.class)
                .extracting(exception -> ((RecordingException) exception).getErrorCode())
                .isEqualTo(RecordingErrorCode.INVALID_RECORDING_DATA);
    }

    @Test
    @DisplayName("종료된 녹음은 재개 요청으로 다시 활성화하지 못한다")
    void resume_failure_alreadyEnded() {
        // given
        RecordingSession session = startRecording();
        session.end(STARTED_AT.plusSeconds(10));

        // when
        Throwable failure = catchThrowable(() -> session.resume(STARTED_AT.plusSeconds(20)));

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_ALREADY_ENDED);
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.ENDED);
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("일시정지 중 종료하면 멈춘 이후의 시간을 누적하지 않는다")
    void end_success_pausedSession() {
        // given
        RecordingSession session = startRecording();
        session.pause(STARTED_AT.plusSeconds(10));

        // when
        session.end(STARTED_AT.plusSeconds(100));

        // then
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(10_000);
        assertThat(session.getEndedAt()).isEqualTo(STARTED_AT.plusSeconds(100));
    }

    @Test
    @DisplayName("일시정지를 반복해도 누적 시간과 생존 시각은 바뀌지 않는다")
    void pause_success_repeated() {
        // given
        RecordingSession session = startRecording();
        session.pause(STARTED_AT.plusSeconds(10));

        // when
        session.pause(STARTED_AT.plusSeconds(100));

        // then
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(10_000);
        assertThat(session.getLastSeenAt()).isEqualTo(STARTED_AT.plusSeconds(10));
        assertThat(session.getPausedAt()).isEqualTo(STARTED_AT.plusSeconds(10));
    }

    @Test
    @DisplayName("일시정지 시각보다 이전의 재개 요청은 상태와 시간을 바꾸지 못한다")
    void resume_failure_timeBeforePause() {
        // given
        RecordingSession session = startRecording();
        session.pause(STARTED_AT.plusSeconds(10));

        // when
        Throwable failure = catchThrowable(() -> session.resume(STARTED_AT.plusSeconds(5)));

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.INVALID_RECORDING_TIME);
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.PAUSED);
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("필수 요청 ID가 없으면 HTTP 검증과 별개로 도메인 생성을 거부한다")
    void start_failure_missingRequestId() {
        // given

        // when
        Throwable failure = catchThrowable(
                () -> RecordingSession.start(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        null,
                        TAB_ID,
                        CONTROL_TOKEN_HASH,
                        STARTED_AT
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.INVALID_RECORDING_DATA);
    }

    @Test
    @DisplayName("녹음 중인 세션을 폐기하면 진행 구간을 누적하고 DISCARDED로 멈춘다")
    void discard_success_recording() {
        // given
        RecordingSession session = startRecording();
        Instant discardedAt = STARTED_AT.plusSeconds(30);

        // when
        session.discard(discardedAt);

        // then
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.DISCARDED);
        assertThat(session.getEndedAt()).isEqualTo(discardedAt);
        assertThat(session.getLastSeenAt()).isEqualTo(discardedAt);
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("일시정지한 세션을 폐기하면 누적 시간을 유지하고 DISCARDED로 멈춘다")
    void discard_success_paused() {
        // given
        RecordingSession session = startRecording();
        session.pause(STARTED_AT.plusSeconds(10));

        // when
        session.discard(STARTED_AT.plusSeconds(40));

        // then
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.DISCARDED);
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("서버 시계가 뒤로 조정돼 폐기 시각이 마지막 활동보다 이르면 마지막 활동 시각으로 폐기한다")
    void discard_success_timeBeforeLastSeenUsesLastSeen() {
        // given
        RecordingSession session = startRecording();
        session.pause(STARTED_AT.plusSeconds(10));

        // when
        session.discard(STARTED_AT.plusSeconds(8));

        // then
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.DISCARDED);
        assertThat(session.getEndedAt()).isEqualTo(STARTED_AT.plusSeconds(10));
        assertThat(session.getLastSeenAt()).isEqualTo(STARTED_AT.plusSeconds(10));
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("녹음 중 폐기 시각이 구간 시작보다 이르면 진행 구간을 음수로 누적하지 않고 마지막 활동 시각으로 폐기한다")
    void discard_success_recordingTimeBeforeLastSeenUsesLastSeen() {
        // given
        RecordingSession session = startRecording();

        // when
        session.discard(STARTED_AT.minusSeconds(2));

        // then
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.DISCARDED);
        assertThat(session.getEndedAt()).isEqualTo(STARTED_AT);
        assertThat(session.getAccumulatedRecordingMillis()).isZero();
    }

    @Test
    @DisplayName("이미 폐기한 세션을 다시 폐기하면 처음 폐기 시각을 유지한다")
    void discard_success_repeated() {
        // given
        RecordingSession session = startRecording();
        session.discard(STARTED_AT.plusSeconds(10));

        // when
        session.discard(STARTED_AT.plusSeconds(20));

        // then
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.DISCARDED);
        assertThat(session.getEndedAt()).isEqualTo(STARTED_AT.plusSeconds(10));
    }

    @Test
    @DisplayName("정상 종료된 세션은 폐기로 바꾸지 않는다")
    void discard_failure_alreadyEnded() {
        // given
        RecordingSession session = startRecording();
        session.end(STARTED_AT.plusSeconds(10));

        // when
        Throwable failure = catchThrowable(() -> session.discard(STARTED_AT.plusSeconds(20)));

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_ALREADY_ENDED);
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.ENDED);
    }

    @Test
    @DisplayName("폐기된 세션은 정상 종료로 바꾸지 않는다")
    void end_failure_alreadyDiscarded() {
        // given
        RecordingSession session = startRecording();
        session.discard(STARTED_AT.plusSeconds(10));

        // when
        Throwable failure = catchThrowable(() -> session.end(STARTED_AT.plusSeconds(20)));

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_ALREADY_DISCARDED);
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.DISCARDED);
    }

    @Test
    @DisplayName("폐기된 세션은 다시 재개하지 않는다")
    void resume_failure_alreadyDiscarded() {
        // given
        RecordingSession session = startRecording();
        session.discard(STARTED_AT.plusSeconds(10));

        // when
        Throwable failure = catchThrowable(() -> session.resume(STARTED_AT.plusSeconds(20)));

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_ALREADY_DISCARDED);
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.DISCARDED);
    }

    @Test
    @DisplayName("같은 Workspace의 시작자는 녹음을 제어할 수 있다")
    void validateControlledBy_success_starter() {
        // given
        RecordingSession session = startRecording();

        // when
        Throwable failure = catchThrowable(
                () -> session.validateControlledBy(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        );

        // then
        assertThat(failure).isNull();
    }

    @Test
    @DisplayName("요청 Workspace가 다르면 녹음이 없는 것으로 거절한다")
    void validateControlledBy_failure_otherWorkspace() {
        // given
        RecordingSession session = startRecording();

        // when
        Throwable failure = catchThrowable(
                () -> session.validateControlledBy(
                        WORKSPACE_ID + 1,
                        MEMBER_ID
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_NOT_FOUND);
    }

    @Test
    @DisplayName("시작자가 아닌 멤버는 녹음을 제어할 수 없다")
    void validateControlledBy_failure_otherMember() {
        // given
        RecordingSession session = startRecording();

        // when
        Throwable failure = catchThrowable(
                () -> session.validateControlledBy(
                        WORKSPACE_ID,
                        MEMBER_ID + 1
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_CONTROL_DENIED);
    }

    @Test
    @DisplayName("일시정지하면 일시정지 시각을 기록한다")
    void pause_success_recordsPausedAt() {
        // given
        RecordingSession session = startRecording();

        // when
        session.pause(STARTED_AT.plusSeconds(10));

        // then
        assertThat(session.getPausedAt()).isEqualTo(STARTED_AT.plusSeconds(10));
    }

    @Test
    @DisplayName("시작 때의 탭 ID와 제어 증명이 같으면 최초 탭으로 인정한다")
    void validateControlProof_success_firstTab() {
        // given
        RecordingSession session = startRecording();

        // when
        Throwable failure = catchThrowable(
                () -> session.validateControlProof(
                        TAB_ID,
                        CONTROL_TOKEN_HASH
                )
        );

        // then
        assertThat(failure).isNull();
    }

    @Test
    @DisplayName("다른 탭 ID로는 녹음을 제어할 수 없다")
    void validateControlProof_failure_otherTab() {
        // given
        RecordingSession session = startRecording();

        // when
        Throwable failure = catchThrowable(
                () -> session.validateControlProof(
                        UUID.randomUUID(),
                        CONTROL_TOKEN_HASH
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_CONTROL_DENIED);
    }

    @Test
    @DisplayName("탭 ID가 같아도 제어 증명이 다르면 녹음을 제어할 수 없다")
    void validateControlProof_failure_otherControlToken() {
        // given
        RecordingSession session = startRecording();

        // when
        Throwable failure = catchThrowable(
                () -> session.validateControlProof(
                        TAB_ID,
                        "f".repeat(64)
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_CONTROL_DENIED);
    }

    @Test
    @DisplayName("30분 녹음하고 90분 일시정지한 뒤 재개하면 누적 시간은 30분이고 재개 시각부터 다시 센다")
    void resume_success_excludesLongPause() {
        // given
        RecordingSession session = startRecording();
        session.pause(STARTED_AT.plusSeconds(30 * 60));
        Instant resumedAt = STARTED_AT.plusSeconds(120 * 60);

        // when
        session.resume(resumedAt);

        // then
        assertThat(session.getStatus()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(30 * 60 * 1000L);
        assertThat(session.getResumedAt()).isEqualTo(resumedAt);
        assertThat(session.getPausedAt()).isNull();
        assertThat(session.getRecordingDurationMillis(resumedAt.plusSeconds(60))).isEqualTo(31 * 60 * 1000L);
    }

    @Test
    @DisplayName("이미 녹음 중인 세션을 다시 재개하면 처음 재개 시각과 누적 시간을 유지한다")
    void resume_success_repeatedKeepsResumedAt() {
        // given
        RecordingSession session = startRecording();
        session.pause(STARTED_AT.plusSeconds(10));
        session.resume(STARTED_AT.plusSeconds(20));

        // when
        session.resume(STARTED_AT.plusSeconds(40));

        // then
        assertThat(session.getResumedAt()).isEqualTo(STARTED_AT.plusSeconds(20));
        assertThat(session.getAccumulatedRecordingMillis()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("일시정지 중에는 재개 시각이 없다")
    void getResumedAt_success_nullWhilePaused() {
        // given
        RecordingSession session = startRecording();

        // when
        session.pause(STARTED_AT.plusSeconds(10));

        // then
        assertThat(session.getResumedAt()).isNull();
    }

    @Test
    @DisplayName("종료된 녹음은 오디오를 업로드할 수 있다")
    void validateAudioUploadable_success_ended() {
        // given
        RecordingSession session = startRecording();
        session.end(STARTED_AT.plusSeconds(10));

        // when
        Throwable failure = catchThrowable(session::validateAudioUploadable);

        // then
        assertThat(failure).isNull();
    }

    @Test
    @DisplayName("녹음 중이거나 일시정지한 녹음은 종료 전이라 오디오를 업로드할 수 없다")
    void validateAudioUploadable_failure_notEnded() {
        // given
        RecordingSession session = startRecording();
        session.pause(STARTED_AT.plusSeconds(10));

        // when
        Throwable failure = catchThrowable(session::validateAudioUploadable);

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_NOT_ENDED);
    }

    @Test
    @DisplayName("폐기된 녹음은 오디오를 업로드할 수 없다")
    void validateAudioUploadable_failure_discarded() {
        // given
        RecordingSession session = startRecording();
        session.discard(STARTED_AT.plusSeconds(10));

        // when
        Throwable failure = catchThrowable(session::validateAudioUploadable);

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_ALREADY_DISCARDED);
    }

    private RecordingSession startRecording() {
        return RecordingSession.start(
                WORKSPACE_ID,
                MEMBER_ID,
                REQUEST_ID,
                TAB_ID,
                CONTROL_TOKEN_HASH,
                STARTED_AT
        );
    }

    @Test
    @DisplayName("녹음이 Workspace 소속 여부를 직접 판단한다")
    void belongsTo_success() {
        // given
        RecordingSession session = startRecording();

        // when & then
        assertThat(session.belongsTo(WORKSPACE_ID)).isTrue();
        assertThat(session.belongsTo(99)).isFalse();
    }

    @Test
    @DisplayName("종료 검증은 업로드 요청 없이도 종료된 녹음을 허용한다")
    void validateEnded_success() {
        // given
        RecordingSession session = startRecording();
        session.end(STARTED_AT.plusSeconds(10));

        // when & then
        assertThat(catchThrowable(session::validateEnded)).isNull();
    }

    @Test
    @DisplayName("종료 검증은 실행 중과 폐기된 녹음을 각각 기존 오류로 거절한다")
    void validateEnded_failure() {
        // given
        RecordingSession session = startRecording();

        // when & then
        assertThat(catchThrowable(session::validateEnded)).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_NOT_ENDED);
        session.discard(STARTED_AT.plusSeconds(10));
        assertThat(catchThrowable(session::validateEnded)).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_ALREADY_DISCARDED);
    }
}
