package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentGenerationJobTest {
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
