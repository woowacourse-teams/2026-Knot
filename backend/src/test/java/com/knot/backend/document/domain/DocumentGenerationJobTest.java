package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentGenerationJobTest {
    @Test
    @DisplayName("실패 기록은 마지막 실패 시각과 168시간 만료 시각을 저장한다")
    void recordFailure_success() {
        // given
        Instant createdAt = Instant.parse("2026-10-06T00:00:00Z");
        Instant failedAt = createdAt.plusSeconds(60);
        DocumentGenerationJob job = DocumentGenerationJob.queue(
                1,
                createdAt
        );

        // when
        job.recordFailure(failedAt);

        // then
        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.FAILED);
        assertThat(job.getLastFailedAt()).isEqualTo(failedAt);
        assertThat(job.getUpdatedAt()).isEqualTo(failedAt);
        assertThat(job.getExpiresAt()).isEqualTo(failedAt.plusSeconds(7 * 24 * 60 * 60));
    }

    @Test
    @DisplayName("새로운 실패가 기록되면 마지막 실패 기준으로 기한을 갱신한다")
    void recordFailure_success_renewDeadline() {
        // given
        Instant createdAt = Instant.parse("2026-10-06T00:00:00Z");
        DocumentGenerationJob job = DocumentGenerationJob.queue(
                1,
                createdAt
        );
        job.recordFailure(createdAt.plusSeconds(60));
        Instant failedAt = createdAt.plusSeconds(120);

        // when
        job.recordFailure(failedAt);

        // then
        assertThat(job.getLastFailedAt()).isEqualTo(failedAt);
        assertThat(job.getExpiresAt()).isEqualTo(failedAt.plusSeconds(7 * 24 * 60 * 60));
    }

    @Test
    @DisplayName("과거·누락·범위 밖 실패 시각은 상태를 변경하지 않는다")
    void recordFailure_failure_invalidTime() {
        // given
        Instant createdAt = Instant.parse("2026-10-06T00:00:00Z");
        DocumentGenerationJob job = DocumentGenerationJob.queue(
                1,
                createdAt
        );

        // when & then
        assertThatThrownBy(() -> job.recordFailure(createdAt.minusSeconds(1))).isInstanceOf(DocumentException.class);
        assertThatThrownBy(() -> job.recordFailure(null)).isInstanceOf(DocumentException.class);
        assertThatThrownBy(() -> job.recordFailure(Instant.MAX)).isInstanceOf(DocumentException.class);
        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.QUEUED);
        assertThat(job.getLastFailedAt()).isNull();
    }
    @Test
    @DisplayName("Job은 입력 원문과 QUEUED 상태로 생성한다")
    void queue_success() {
        // given
        Instant time = Instant.parse("2026-10-06T00:00:00Z");

        // when
        DocumentGenerationJob job = DocumentGenerationJob.queue(
                1,
                time
        );

        // then
        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.QUEUED);
        assertThat(job.getUpdatedAt()).isEqualTo(time);
    }

    @Test
    @DisplayName("입력 원문 ID가 잘못된 Job은 생성하지 못한다")
    void queue_failure_invalidTranscript() {
        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationJob.queue(
                        0,
                        Instant.now()
                )
        ).isInstanceOf(DocumentException.class);
    }
}
