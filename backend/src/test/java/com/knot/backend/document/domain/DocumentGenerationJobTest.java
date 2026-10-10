package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentGenerationJobTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-06T00:00:00Z");
    private static final Instant FAILED_AT = CREATED_AT.plusSeconds(60);

    @Test
    @DisplayName("첫 접수는 최초 시도 1회이며 사용자·자동 재시도는 0회다")
    void queue_success_initialAttemptCounts() {
        // when
        DocumentGenerationJob job = DocumentGenerationJob.queueClassification(
                1,
                1,
                CREATED_AT
        );

        // then
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getUserRetryCount()).isZero();
        assertThat(job.getAutomaticRetryCount()).isZero();
    }

    @Test
    @DisplayName("실패 Job은 같은 원문·생성 시각을 유지하고 사용자 재시도를 접수한다")
    void retryByUser_success() {
        // given
        DocumentGenerationJob job = failedJob();
        Instant acceptedAt = FAILED_AT.plusSeconds(1);

        // when
        job.retryByUser(acceptedAt);

        // then
        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.QUEUED);
        assertThat(job.getTranscriptId()).isEqualTo(1);
        assertThat(job.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(job.getUpdatedAt()).isEqualTo(acceptedAt);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getUserRetryCount()).isEqualTo(1);
        assertThat(job.getLastFailedAt()).isEqualTo(FAILED_AT);
        assertThat(job.getExpiresAt()).isEqualTo(FAILED_AT.plusSeconds(168 * 3600));
    }

    @Test
    @DisplayName("사용자 재시도 3회까지만 허용하고 재실패 때 횟수를 초기화하지 않는다")
    void retryByUser_failure_fourthRetry() {
        // given
        DocumentGenerationJob job = failedJob();
        for (int retry = 1; retry <= 3; retry++) {
            job.retryByUser(FAILED_AT.plusSeconds(retry * 2));
            job.recordFailure(FAILED_AT.plusSeconds(retry * 2 + 1));
        }
        Instant lastFailure = job.getLastFailedAt();

        // when & then
        assertRetryDenied(
                job,
                lastFailure.plusSeconds(1)
        );
        assertThat(job.getUserRetryCount()).isEqualTo(3);
        assertThat(job.getAttemptCount()).isEqualTo(4);
        assertThat(job.getExpiresAt()).isEqualTo(lastFailure.plusSeconds(168 * 3600));
    }

    @Test
    @DisplayName("자동 재시도 이력은 전체 횟수에 포함되지만 사용자 한도를 차감하지 않는다")
    void retryByUser_success_separateAutomaticAttempts() {
        // given
        DocumentGenerationJob job = failedJob();
        ReflectionTestUtils.setField(
                job,
                "automaticRetryCount",
                5
        );
        ReflectionTestUtils.setField(
                job,
                "attemptCount",
                6
        );

        // when
        job.retryByUser(FAILED_AT.plusSeconds(1));

        // then
        assertThat(job.getAutomaticRetryCount()).isEqualTo(5);
        assertThat(job.getUserRetryCount()).isEqualTo(1);
        assertThat(job.getAttemptCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("168시간 만료 직전은 접수할 수 있다")
    void retryByUser_success_beforeDeadline() {
        // given
        DocumentGenerationJob job = failedJob();

        // when
        job.retryByUser(
                job.getExpiresAt()
                        .minusNanos(1000)
        );

        // then
        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.QUEUED);
    }

    @Test
    @DisplayName("정확한 만료 시각과 이후는 접수하지 않고 상태·횟수를 유지한다")
    void retryByUser_failure_expired() {
        // given
        DocumentGenerationJob job = failedJob();

        // when & then
        assertRetryDenied(
                job,
                job.getExpiresAt()
        );
        assertRetryDenied(
                job,
                job.getExpiresAt()
                        .plusSeconds(1)
        );
    }

    @ParameterizedTest
    @EnumSource(value = DocumentGenerationJobStatus.class, names = {"QUEUED", "RUNNING", "SUCCEEDED"})
    @DisplayName("실패 이력이 있어도 현재 FAILED가 아니면 재시도하지 않는다")
    void retryByUser_failure_notFailed(DocumentGenerationJobStatus status) {
        // given
        DocumentGenerationJob job = failedJob();
        ReflectionTestUtils.setField(
                job,
                "status",
                status
        );

        // when & then
        assertRetryDenied(
                job,
                FAILED_AT.plusSeconds(1)
        );
    }

    @Test
    @DisplayName("누락·과거·범위 밖 접수 시각은 횟수를 변경하지 않는다")
    void retryByUser_failure_invalidTime() {
        // given
        DocumentGenerationJob job = failedJob();

        // when & then
        assertRetryDenied(
                job,
                null
        );
        assertRetryDenied(
                job,
                FAILED_AT.minusSeconds(1)
        );
        assertRetryDenied(
                job,
                Instant.MAX
        );
    }

    @Test
    @DisplayName("전체 시도 횟수의 정수 범위를 넘는 접수는 상태 변경 전에 거절한다")
    void retryByUser_failure_attemptOverflow() {
        // given
        DocumentGenerationJob job = failedJob();
        ReflectionTestUtils.setField(
                job,
                "attemptCount",
                Integer.MAX_VALUE
        );
        ReflectionTestUtils.setField(
                job,
                "automaticRetryCount",
                Integer.MAX_VALUE - 1
        );

        // when & then
        assertRetryDenied(
                job,
                FAILED_AT.plusSeconds(1)
        );
    }

    private DocumentGenerationJob failedJob() {
        DocumentGenerationJob job = DocumentGenerationJob.queueClassification(
                1,
                1,
                CREATED_AT
        );
        job.recordFailure(FAILED_AT);
        return job;
    }

    private void assertRetryDenied(
            DocumentGenerationJob job,
            Instant acceptedAt
    ) {
        DocumentGenerationJobStatus status = job.getStatus();
        Instant updatedAt = job.getUpdatedAt();
        int attempts = job.getAttemptCount();
        int retries = job.getUserRetryCount();
        assertThatThrownBy(() -> job.retryByUser(acceptedAt)).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.RETRY_NOT_ALLOWED.getMessage());
        assertThat(job.getStatus()).isEqualTo(status);
        assertThat(job.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(job.getAttemptCount()).isEqualTo(attempts);
        assertThat(job.getUserRetryCount()).isEqualTo(retries);
    }

    @Test
    @DisplayName("실패 기록은 마지막 실패 시각과 168시간 만료 시각을 저장한다")
    void recordFailure_success() {
        // given
        Instant createdAt = Instant.parse("2026-10-06T00:00:00Z");
        Instant failedAt = createdAt.plusSeconds(60);
        DocumentGenerationJob job = DocumentGenerationJob.queueClassification(
                1,
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
        DocumentGenerationJob job = DocumentGenerationJob.queueClassification(
                1,
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
        DocumentGenerationJob job = DocumentGenerationJob.queueClassification(
                1,
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
        DocumentGenerationJob job = DocumentGenerationJob.queueClassification(
                1,
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
                () -> DocumentGenerationJob.queueClassification(
                        1,
                        0,
                        Instant.now()
                )
        ).isInstanceOf(DocumentException.class);
    }
}
