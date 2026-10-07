package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentGenerationJobCursorTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-07T00:00:00.123456Z");

    @Test
    @DisplayName("커서는 조회 범위와 마이크로초 생성 시각·Job ID를 유지한다")
    void parse_success_roundTrip() {
        // given
        String encoded = DocumentGenerationJobCursor.of(
                1,
                2,
                CREATED_AT,
                3
        )
                .encode();

        // when
        DocumentGenerationJobCursor cursor = DocumentGenerationJobCursor.parse(
                encoded,
                1,
                2
        );

        // then
        assertThat(cursor.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(cursor.getJobId()).isEqualTo(3);
        assertThat(cursor.encode()).isEqualTo(encoded);
    }

    @Test
    @DisplayName("다른 Workspace 또는 다른 멤버의 커서는 거절한다")
    void parse_failure_scopeMismatch() {
        // given
        String encoded = DocumentGenerationJobCursor.of(
                1,
                2,
                CREATED_AT,
                3
        )
                .encode();

        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationJobCursor.parse(
                        encoded,
                        4,
                        2
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> DocumentGenerationJobCursor.parse(
                        encoded,
                        1,
                        4
                )
        ).isInstanceOf(DocumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-cursor!"})
    @DisplayName("누락·빈 값·인코딩 오류 커서는 거절한다")
    void parse_failure_invalidEncodedCursor(String encoded) {
        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationJobCursor.parse(
                        encoded,
                        1,
                        2
                )
        ).isInstanceOf(DocumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2|1|2|2026-10-07T00:00:00Z|3", "1|1|2|2026-10-07T00:00:00Z",
            "1|1|2|2026-10-07T00:00:00Z|3|extra", "1|0|2|2026-10-07T00:00:00Z|3", "1|1|0|2026-10-07T00:00:00Z|3",
            "1|1|2|invalid|3", "1|1|2|2026-10-07T00:00:00Z|0", "1|1|2|0000-01-01T00:00:00Z|3",
            "1|1|2|+10000-01-01T00:00:00Z|3", "1|9223372036854775808|2|2026-10-07T00:00:00Z|3"})
    @DisplayName("커서의 버전·필드·식별자·시각 범위 오류는 거절한다")
    void parse_failure_invalidPayload(String payload) {
        // given
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationJobCursor.parse(
                        encoded,
                        1,
                        2
                )
        ).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("과도하게 긴 커서는 디코딩 전에 거절한다")
    void parse_failure_oversizedCursor() {
        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationJobCursor.parse(
                        "a".repeat(513),
                        1,
                        2
                )
        ).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("누락된 경계 시각은 거절한다")
    void of_failure_missingCreatedAt() {
        // when & then
        assertThatThrownBy(
                () -> DocumentGenerationJobCursor.of(
                        1,
                        2,
                        null,
                        3
                )
        ).isInstanceOf(DocumentException.class);
    }
}
