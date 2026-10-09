package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentGenerationBatchTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    @DisplayName("접수한 원문은 분류 대기 상태로 고정된다")
    void accept_success() {
        // when
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                1,
                2,
                NOW
        );
        // then
        assertThat(batch.getTranscriptId()).isEqualTo(2);
        assertThat(batch.getTopicRegistrationState()).isEqualTo(DocumentTopicRegistrationState.WAITING_CLASSIFICATION);
    }

    @Test
    @DisplayName("없는 녹음 또는 원문 식별자로 접수할 수 없다")
    void accept_failure_invalidIdentifier() {
        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationBatch.accept(
                        0,
                        2,
                        NOW
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> DocumentGenerationBatch.accept(
                        1,
                        0,
                        NOW
                )
        ).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("주제 순서를 보존하고 이후 다른 분류 결과로 덮어쓰지 않는다")
    void registerTopics_success_frozenOrder() {
        // given
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                1,
                2,
                NOW
        );
        // when
        batch.registerTopics(
                List.of(
                        "검색",
                        "알림"
                ),
                NOW.plusSeconds(1)
        );
        // then
        assertThat(batch.getTopics()).containsExactly(
                "검색",
                "알림"
        );
        assertThat(batch.getTopicRegistrationState()).isEqualTo(DocumentTopicRegistrationState.TOPICS_REGISTERED);
        assertThatThrownBy(
                () -> batch.registerTopics(
                        List.of("다른 주제"),
                        NOW.plusSeconds(2)
                )
        ).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("중복 주제와 공백 주제를 등록하지 않는다")
    void registerTopics_failure_invalidTopics() {
        // given
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                1,
                2,
                NOW
        );
        // when & then
        assertThatThrownBy(
                () -> batch.registerTopics(
                        List.of(
                                "검색",
                                "검색"
                        ),
                        NOW
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> batch.registerTopics(
                        List.of("\u00a0"),
                        NOW
                )
        ).isInstanceOf(DocumentException.class);
        assertThat(batch.getTopicRegistrationState()).isEqualTo(DocumentTopicRegistrationState.WAITING_CLASSIFICATION);
    }

    @Test
    @DisplayName("빈 분류 결과는 내용 없음과 정리 요청 시각을 함께 남긴다")
    void registerTopics_success_noContent() {
        // given
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                1,
                2,
                NOW
        );
        // when
        batch.registerTopics(
                List.of(),
                NOW.plusSeconds(1)
        );
        // then
        assertThat(batch.getTopicRegistrationState()).isEqualTo(DocumentTopicRegistrationState.NO_CONTENT);
        assertThat(batch.getCleanupRequestedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    @DisplayName("접수보다 이른 등록 시각은 거절한다")
    void registerTopics_failure_invalidTime() {
        // given
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                1,
                2,
                NOW
        );
        // when & then
        assertThatThrownBy(
                () -> batch.registerTopics(
                        List.of("검색"),
                        NOW.minusSeconds(1)
                )
        ).isInstanceOf(DocumentException.class);
    }
}
