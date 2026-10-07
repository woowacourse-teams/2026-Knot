package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentCursorTest {
    @Test
    @DisplayName("지나치게 긴 커서는 디코딩 전에 거절한다")
    void parse_failure_oversizedCursor() {
        assertThatThrownBy(
                () -> DocumentCursor.parse(
                        "a".repeat(513),
                        1,
                        2,
                        null,
                        null
                )
        ).isInstanceOf(DocumentException.class);
    }
    private static final Instant TIME = Instant.parse("2026-10-06T00:00:00.123456Z");

    @Test
    @DisplayName("커서의 범위와 마이크로초 경계가 왕복 후 유지된다")
    void parse_success_precisionAndFilters() {
        // given
        String encoded = DocumentCursor.of(
                1,
                2,
                MyConfirmationState.PENDING,
                42L,
                TIME,
                301
        )
                .encode();
        // when
        DocumentCursor cursor = DocumentCursor.parse(
                encoded,
                1,
                2,
                MyConfirmationState.PENDING,
                42L
        );
        // then
        assertThat(cursor.getCreatedAt()).isEqualTo(TIME);
        assertThat(cursor.getDocumentId()).isEqualTo(301);
    }

    @Test
    @DisplayName("필터 없는 커서도 복원한다")
    void parse_success_unfiltered() {
        // given
        String encoded = DocumentCursor.of(
                1,
                2,
                null,
                null,
                TIME,
                301
        )
                .encode();
        // when & then
        assertThat(
                DocumentCursor.parse(
                        encoded,
                        1,
                        2,
                        null,
                        null
                )
                        .getDocumentId()
        ).isEqualTo(301);
    }

    @Test
    @DisplayName("다른 사용자나 Workspace나 필터에 커서를 재사용하지 못한다")
    void parse_failure_scopeMismatch() {
        // given
        String encoded = DocumentCursor.of(
                1,
                2,
                null,
                null,
                TIME,
                301
        )
                .encode();
        // when & then
        assertThatThrownBy(
                () -> DocumentCursor.parse(
                        encoded,
                        3,
                        2,
                        null,
                        null
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> DocumentCursor.parse(
                        encoded,
                        1,
                        3,
                        null,
                        null
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> DocumentCursor.parse(
                        encoded,
                        1,
                        2,
                        MyConfirmationState.PENDING,
                        null
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> DocumentCursor.parse(
                        encoded,
                        1,
                        2,
                        null,
                        42L
                )
        ).isInstanceOf(DocumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%%%", "", "MQ", "%%%invalid"})
    @DisplayName("잘못된 형식이나 과도한 길이의 커서를 거절한다")
    void parse_failure_invalidEncoding(String encoded) {
        // when & then
        assertThatThrownBy(
                () -> DocumentCursor.parse(
                        encoded,
                        1,
                        2,
                        null,
                        null
                )
        ).isInstanceOf(DocumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2|1|2|ALL|ALL|2026-10-06T00:00:00Z|301", "1|1|2|INVALID|ALL|2026-10-06T00:00:00Z|301",
            "1|1|2|ALL|0|2026-10-06T00:00:00Z|301", "1|1|2|ALL|ALL|invalid|301", "1|1|2|ALL|ALL|2026-10-06T00:00:00Z|0",
            "1|1|2|ALL|ALL|+100000-01-01T00:00:00Z|301", "1|1|2|ALL|ALL|2026-10-06T00:00:00Z|9223372036854775808"})
    @DisplayName("커서 내부의 버전과 필터와 경계값을 검증한다")
    void parse_failure_invalidPayload(String payload) {
        // given
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        // when & then
        assertThatThrownBy(
                () -> DocumentCursor.parse(
                        encoded,
                        1,
                        2,
                        null,
                        null
                )
        ).isInstanceOf(DocumentException.class);
    }
}
