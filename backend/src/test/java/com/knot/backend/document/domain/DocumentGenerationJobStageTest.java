package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentGenerationJobStageTest {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    @DisplayName("분류 Job과 주제별 생성 Job은 같은 입력을 사용하되 단계를 구분한다")
    void queue_success_stage() {
        // when
        DocumentGenerationJob classification = DocumentGenerationJob.queueClassification(
                1,
                2,
                NOW
        );
        DocumentGenerationJob generation = DocumentGenerationJob.queueGeneration(
                1,
                2,
                "검색",
                NOW
        );
        // then
        assertThat(classification.getStage()).isEqualTo(DocumentGenerationJobStage.CLASSIFICATION);
        assertThat(classification.getTopic()).isNull();
        assertThat(generation.getStage()).isEqualTo(DocumentGenerationJobStage.GENERATION);
        assertThat(generation.getTopic()).isEqualTo("검색");
    }

    @Test
    @DisplayName("생성 Job에는 유효한 Batch와 주제가 필요하다")
    void queue_failure_invalidTarget() {
        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationJob.queueClassification(
                        0,
                        2,
                        NOW
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> DocumentGenerationJob.queueGeneration(
                        1,
                        2,
                        " ",
                        NOW
                )
        ).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("실행 시작은 접수 횟수를 증가시키지 않고 현재 시도만 성공시킨다")
    void startRunning_success() {
        // given
        DocumentGenerationJob job = DocumentGenerationJob.queueClassification(
                1,
                2,
                NOW
        );
        // when
        job.startRunning(NOW.plusSeconds(1));
        job.validateRunningAttempt(1);
        job.recordSuccess(NOW.plusSeconds(2));
        // then
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("실행 중이 아니거나 이전 시도의 성공 결과는 반영하지 않는다")
    void recordSuccess_failure_staleAttempt() {
        // given
        DocumentGenerationJob job = DocumentGenerationJob.queueClassification(
                1,
                2,
                NOW
        );
        // when & then
        assertThatThrownBy(() -> job.recordSuccess(NOW)).isInstanceOf(DocumentException.class);
        job.startRunning(NOW);
        assertThatThrownBy(() -> job.startRunning(NOW)).isInstanceOf(DocumentException.class);
        assertThatThrownBy(() -> job.validateRunningAttempt(2)).isInstanceOf(DocumentException.class);
        assertThatThrownBy(() -> job.recordSuccess(NOW.minusSeconds(1))).isInstanceOf(DocumentException.class);
    }
}
