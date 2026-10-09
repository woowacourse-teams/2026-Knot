package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentGenerationExecutionTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    @DisplayName("확보는 횟수를 올리지 않고 실행 기한을 저장한다")
    void startRunning_success_preservesAcceptedAttempt() {
        // given
        DocumentGenerationJob job = job();
        // when
        job.startRunning(
                NOW,
                NOW.plusSeconds(150)
        );
        // then
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getExecutionDeadlineAt()).isEqualTo(NOW.plusSeconds(150));
        assertThat(job.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("실행 기한과 같은 시각의 결과는 거절한다")
    void validateRunningAttemptAt_failure_expiredBoundary() {
        // given
        DocumentGenerationJob job = job();
        job.startRunning(
                NOW,
                NOW.plusSeconds(150)
        );
        // when & then
        assertThatThrownBy(
                () -> job.validateRunningAttemptAt(
                        1,
                        NOW.plusSeconds(150)
                )
        ).isInstanceOf(DocumentException.class);
        job.validateRunningAttemptAt(
                1,
                NOW.plusSeconds(149)
        );
    }

    @Test
    @DisplayName("자동 접수는 사용자 횟수와 분리되며 예약 시각 전에 확보되지 않는다")
    void retryAutomatically_success_separateCounters() {
        // given
        DocumentGenerationJob job = job();
        job.startRunning(
                NOW,
                NOW.plusSeconds(150)
        );
        job.recordFailure(
                NOW.plusSeconds(1),
                DocumentGenerationFailureCause.TIMEOUT
        );
        // when
        job.retryAutomatically(
                NOW.plusSeconds(1),
                NOW.plusSeconds(6)
        );
        // then
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getAutomaticRetryCount()).isEqualTo(1);
        assertThat(job.getUserRetryCount()).isZero();
        assertThat(job.isReadyAt(NOW.plusSeconds(5))).isFalse();
        assertThat(job.isReadyAt(NOW.plusSeconds(6))).isTrue();
        assertThat(job.getExecutionDeadlineAt()).isNull();
        assertThat(job.getFailureCause()).isNull();
    }

    @Test
    @DisplayName("사용자 재접수도 자동 재시도 한도를 초기화하지 않는다")
    void retryAutomatically_failure_lifetimeLimit() {
        // given
        DocumentGenerationJob job = job();
        job.recordFailure(
                NOW,
                DocumentGenerationFailureCause.UNAVAILABLE
        );
        job.retryAutomatically(
                NOW,
                NOW
        );
        job.recordFailure(
                NOW,
                DocumentGenerationFailureCause.UNAVAILABLE
        );
        job.retryByUser(NOW);
        job.recordFailure(
                NOW,
                DocumentGenerationFailureCause.UNAVAILABLE
        );
        // when & then
        assertThatThrownBy(
                () -> job.retryAutomatically(
                        NOW,
                        NOW
                )
        ).isInstanceOf(DocumentException.class);
        assertThat(job.getAttemptCount()).isEqualTo(3);
        assertThat(job.getUserRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("일부 실패만 재접수하며 나머지 성공 집계를 보존한다")
    void recordJobTransition_success_partialRetry() {
        // given
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                1,
                1,
                NOW
        );
        batch.registerTopics(
                List.of(
                        "A",
                        "B",
                        "C"
                ),
                NOW
        );
        for (int index = 0; index < 2; index++) {
            batch.recordJobTransition(
                    DocumentGenerationJobStage.GENERATION,
                    DocumentGenerationJobStatus.QUEUED,
                    DocumentGenerationJobStatus.RUNNING,
                    NOW
            );
            batch.recordJobTransition(
                    DocumentGenerationJobStage.GENERATION,
                    DocumentGenerationJobStatus.RUNNING,
                    DocumentGenerationJobStatus.SUCCEEDED,
                    NOW
            );
        }
        batch.recordJobTransition(
                DocumentGenerationJobStage.GENERATION,
                DocumentGenerationJobStatus.QUEUED,
                DocumentGenerationJobStatus.FAILED,
                NOW
        );
        assertThat(batch.getProcessingStatus()).isEqualTo(DocumentGenerationProcessingStatus.FAILED);
        // when
        batch.recordJobTransition(
                DocumentGenerationJobStage.GENERATION,
                DocumentGenerationJobStatus.FAILED,
                DocumentGenerationJobStatus.QUEUED,
                NOW
        );
        // then
        assertThat(batch.getSucceededCount()).isEqualTo(2);
        assertThat(batch.getFailedCount()).isZero();
        assertThat(batch.getQueuedCount()).isEqualTo(1);
        assertThat(batch.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("집계에 없는 상태를 차감하면 거절한다")
    void recordJobTransition_failure_negativeCounter() {
        // given
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                1,
                1,
                NOW
        );
        batch.registerTopics(
                List.of("A"),
                NOW
        );
        // when & then
        assertThatThrownBy(
                () -> batch.recordJobTransition(
                        DocumentGenerationJobStage.GENERATION,
                        DocumentGenerationJobStatus.RUNNING,
                        DocumentGenerationJobStatus.SUCCEEDED,
                        NOW
                )
        ).isInstanceOf(DocumentException.class);
        assertThat(batch.getQueuedCount()).isEqualTo(1);
        assertThat(batch.getSucceededCount()).isZero();
    }

    private DocumentGenerationJob job() {
        return DocumentGenerationJob.queueClassification(
                1,
                1,
                NOW
        );
    }
}
