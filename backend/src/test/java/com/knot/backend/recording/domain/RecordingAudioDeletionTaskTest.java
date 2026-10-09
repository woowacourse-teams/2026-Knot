package com.knot.backend.recording.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingAudioDeletionTaskTest {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    @DisplayName("파일 삭제 성공은 기록한 쓰기 가능 기간 이후에 최종 완료한다")
    void recordSuccess_success_confirmationWindow() {
        // given
        RecordingAudioDeletionTask task = RecordingAudioDeletionTask.request(
                1,
                "key",
                NOW,
                NOW.plusSeconds(10)
        );
        task.claim(
                NOW,
                NOW.plusSeconds(5)
        );
        // when
        task.recordSuccess(
                1,
                NOW.plusSeconds(1)
        );
        // then
        assertThat(task.getStatus()).isEqualTo(RecordingAudioDeletionStatus.PENDING);
        assertThat(task.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
        task.claim(
                NOW.plusSeconds(10),
                NOW.plusSeconds(20)
        );
        task.recordSuccess(
                2,
                NOW.plusSeconds(11)
        );
        assertThat(task.getStatus()).isEqualTo(RecordingAudioDeletionStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("중단된 삭제 작업을 회수하면 이전 시도의 결과는 무시한다")
    void recordSuccess_success_staleAttemptIgnored() {
        // given
        RecordingAudioDeletionTask task = RecordingAudioDeletionTask.request(
                1,
                "key",
                NOW,
                NOW
        );
        task.claim(
                NOW,
                NOW.plusSeconds(5)
        );
        // when
        task.claim(
                NOW.plusSeconds(5),
                NOW.plusSeconds(10)
        );
        task.recordSuccess(
                1,
                NOW.plusSeconds(6)
        );
        // then
        assertThat(task.getStatus()).isEqualTo(RecordingAudioDeletionStatus.RUNNING);
        task.recordFailure(
                2,
                NOW.plusSeconds(6),
                NOW.plusSeconds(20),
                "AUDIO_STORAGE_UNAVAILABLE"
        );
        assertThat(task.getStatus()).isEqualTo(RecordingAudioDeletionStatus.PENDING);
        assertThat(task.isReadyAt(NOW.plusSeconds(19))).isFalse();
        assertThat(task.isReadyAt(NOW.plusSeconds(20))).isTrue();
    }

    @Test
    @DisplayName("준비되지 않은 작업의 중복 점유와 잘못된 삭제 요청은 거절한다")
    void claim_failure_unready() {
        // given
        RecordingAudioDeletionTask task = RecordingAudioDeletionTask.request(
                1,
                "key",
                NOW,
                NOW
        );
        task.claim(
                NOW,
                NOW.plusSeconds(5)
        );
        // when & then
        assertThatThrownBy(
                () -> task.claim(
                        NOW,
                        NOW.plusSeconds(5)
                )
        ).isInstanceOf(RecordingException.class);
        assertThatThrownBy(
                () -> RecordingAudioDeletionTask.request(
                        0,
                        "key",
                        NOW,
                        NOW
                )
        ).isInstanceOf(RecordingException.class);
    }
}
