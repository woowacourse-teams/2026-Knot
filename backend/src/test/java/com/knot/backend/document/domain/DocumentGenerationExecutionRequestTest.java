package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentGenerationExecutionRequestTest {

    @Test
    @DisplayName("재접수 기록은 Job과 회차·접수 시각만 보관한다")
    void accept_success() {
        // given
        Instant acceptedAt = Instant.parse("2026-10-07T00:00:00Z");

        // when
        DocumentGenerationExecutionRequest request = DocumentGenerationExecutionRequest.accept(
                88,
                2,
                acceptedAt
        );

        // then
        assertThat(request.getJobId()).isEqualTo(88);
        assertThat(request.getAttemptCount()).isEqualTo(2);
        assertThat(request.getAcceptedAt()).isEqualTo(acceptedAt);
    }

    @Test
    @DisplayName("Job·회차·접수 시각이 잘못된 기록은 생성하지 않는다")
    void accept_failure_invalidData() {
        // given
        Instant now = Instant.parse("2026-10-07T00:00:00Z");

        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationExecutionRequest.accept(
                        0,
                        2,
                        now
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> DocumentGenerationExecutionRequest.accept(
                        1,
                        0,
                        now
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> DocumentGenerationExecutionRequest.accept(
                        1,
                        2,
                        null
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> DocumentGenerationExecutionRequest.accept(
                        1,
                        2,
                        Instant.MAX
                )
        ).isInstanceOf(DocumentException.class);
    }
}
