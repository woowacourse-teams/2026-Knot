package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentRetentionDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    @DisplayName("실패 Job은 정확히 7일에 만료하며 재시도 접수 후에는 정리하지 않는다")
    void isRetentionExpired_success_boundary() {
        // given
        DocumentGenerationJob job = DocumentGenerationJob.queueClassification(
                1,
                2,
                NOW
        );
        job.startRunning(NOW);
        job.recordFailure(NOW);
        Instant expires = NOW.plus(Duration.ofDays(7));
        // when & then
        assertThat(job.isRetentionExpired(expires.minusNanos(1000))).isFalse();
        assertThat(job.isRetentionExpired(expires)).isTrue();
        assertThat(job.isRetentionExpired(expires.plusSeconds(1))).isTrue();
        job.retryByUser(expires.minusSeconds(1));
        assertThat(job.isRetentionExpired(expires)).isFalse();
    }

    @Test
    @DisplayName("내용 없음 입력을 해제해도 결과와 동일 입력 접수 기록은 유지한다")
    void releaseInput_success_noContent() {
        // given
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                1,
                2,
                NOW
        );
        batch.registerTopics(
                List.of(),
                NOW
        );
        // when
        batch.releaseInput(
                2,
                NOW.plusSeconds(1)
        );
        batch.releaseInput(
                2,
                NOW.plusSeconds(2)
        );
        // then
        assertThat(batch.getTranscriptId()).isNull();
        assertThat(batch.getReleasedTranscriptId()).isEqualTo(2);
        assertThat(batch.getInputReleasedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(batch.getProcessingStatus()).isEqualTo(DocumentGenerationProcessingStatus.NO_CONTENT);
        batch.validateInput(2);
        assertThatThrownBy(() -> batch.validateInput(3)).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("분류 실패 입력 해제 후에도 실패 결과를 유지한다")
    void releaseInput_success_classificationFailure() {
        // given
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                1,
                2,
                NOW
        );
        batch.recordJobTransition(
                DocumentGenerationJobStage.CLASSIFICATION,
                DocumentGenerationJobStatus.QUEUED,
                DocumentGenerationJobStatus.FAILED,
                NOW
        );
        // when
        batch.releaseInput(
                2,
                NOW.plus(Duration.ofDays(7))
        );
        // then
        assertThat(batch.getProcessingStatus()).isEqualTo(DocumentGenerationProcessingStatus.FAILED);
        assertThat(batch.getFinishedAt()).isEqualTo(NOW);
        batch.validateInput(2);
    }

    @Test
    @DisplayName("실행할 작업이 남은 Batch 입력은 해제하지 않는다")
    void releaseInput_failure_activeBatch() {
        // given
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                1,
                2,
                NOW
        );
        // when & then
        assertThatThrownBy(
                () -> batch.releaseInput(
                        2,
                        NOW
                )
        ).isInstanceOf(DocumentException.class);
        assertThat(batch.getTranscriptId()).isEqualTo(2);
    }

    @Test
    @DisplayName("사용자 재시도 후 다시 실패하면 기한만 갱신하고 재시도 횟수는 유지한다")
    void isRetentionExpired_successRenewedFailureDeadline() {
        // given
        DocumentGenerationJob job = DocumentGenerationJob.queueClassification(
                1,
                2,
                NOW
        );
        job.startRunning(NOW);
        job.recordFailure(NOW);
        Instant retried = NOW.plusSeconds(60);
        job.retryByUser(retried);
        job.startRunning(retried);

        // when
        job.recordFailure(retried);

        // then
        assertThat(job.isRetentionExpired(NOW.plus(Duration.ofDays(7)))).isFalse();
        assertThat(job.isRetentionExpired(retried.plus(Duration.ofDays(7)))).isTrue();
        assertThat(job.getUserRetryCount()).isEqualTo(1);
    }
}
