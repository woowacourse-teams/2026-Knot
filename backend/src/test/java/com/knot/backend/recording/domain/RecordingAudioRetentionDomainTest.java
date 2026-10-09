package com.knot.backend.recording.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingAudioRetentionDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    @DisplayName("오디오 보존은 최초 업로드 완료 확인부터 정확히 30일이다")
    void isRetentionExpired_success_completionBoundary() {
        // given
        RecordingAudioUpload upload = RecordingAudioUpload.reserve(
                1L,
                "key",
                "audio/webm",
                100,
                NOW
        );
        upload.complete(
                100,
                "audio/webm",
                NOW.plusSeconds(60)
        );
        Instant expires = NOW.plusSeconds(60)
                .plus(Duration.ofDays(30));
        // when & then
        assertThat(upload.isRetentionExpired(expires.minusNanos(1000))).isFalse();
        assertThat(upload.isRetentionExpired(expires)).isTrue();
        upload.recordDeleted(expires);
        upload.recordDeleted(expires.plusSeconds(1));
        assertThat(upload.getDeletedAt()).isEqualTo(expires);
        assertThat(upload.isCompleted()).isTrue();
        assertThat(upload.getCompletedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("예약만 된 오디오는 30일 보존 정리나 완료 기록으로 처리하지 않는다")
    void recordDeleted_failure_uncompletedUpload() {
        // given
        RecordingAudioUpload upload = RecordingAudioUpload.reserve(
                1L,
                "key",
                "audio/webm",
                100,
                NOW
        );
        // when & then
        assertThat(upload.isRetentionExpired(NOW.plus(Duration.ofDays(31)))).isFalse();
        assertThatThrownBy(() -> upload.recordDeleted(NOW)).isInstanceOf(RecordingException.class);
    }

    @Test
    @DisplayName("URL 재발급의 가장 늦은 만료를 보존하고 완료 뒤에는 새 발급을 거절한다")
    void recordUploadUrlExpiry_success_lastExpiry() {
        // given
        RecordingAudioUpload upload = RecordingAudioUpload.reserve(
                1L,
                "key",
                "audio/webm",
                100,
                NOW
        );
        // when
        upload.recordUploadUrlExpiry(NOW.plusSeconds(120));
        upload.recordUploadUrlExpiry(NOW.plusSeconds(60));
        // then
        assertThat(upload.getLastUploadUrlExpiresAt()).isEqualTo(NOW.plusSeconds(120));
        upload.complete(
                100,
                "audio/webm",
                NOW
        );
        assertThatThrownBy(() -> upload.recordUploadUrlExpiry(NOW.plusSeconds(180)))
                .isInstanceOf(RecordingException.class);
    }
}
