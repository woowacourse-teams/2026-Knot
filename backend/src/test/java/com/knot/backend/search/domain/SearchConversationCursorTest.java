package com.knot.backend.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SearchConversationCursorTest {
    private static final Instant TIME = Instant.parse("2026-10-10T00:00:00.123456Z");

    @Test
    @DisplayName("URL 안전 커서로 범위와 마이크로초 정렬 위치를 복원한다")
    void encodeAndParse_success() {
        // given
        String encoded = SearchConversationCursor.of(
                1,
                2,
                TIME,
                301
        )
                .encode();

        // when
        SearchConversationCursor cursor = SearchConversationCursor.parse(
                encoded,
                1,
                2
        );

        // then
        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(cursor.getUpdatedAt()).isEqualTo(TIME);
        assertThat(cursor.getConversationId()).isEqualTo(301);
    }

    @Test
    @DisplayName("같은 시각의 서로 다른 대화 ID를 구분한다")
    void parseTiedTime_success() {
        // given
        String first = SearchConversationCursor.of(
                1,
                2,
                TIME,
                301
        )
                .encode();
        String second = SearchConversationCursor.of(
                1,
                2,
                TIME,
                300
        )
                .encode();

        // when
        SearchConversationCursor cursor = SearchConversationCursor.parse(
                second,
                1,
                2
        );

        // then
        assertThat(second).isNotEqualTo(first);
        assertThat(cursor.getConversationId()).isEqualTo(300);
    }

    @Test
    @DisplayName("다른 Workspace의 커서는 거절한다")
    void parseOtherWorkspace_failure() {
        // given
        String encoded = SearchConversationCursor.of(
                1,
                2,
                TIME,
                301
        )
                .encode();

        // when & then
        assertInvalidCursor(
                encoded,
                3,
                2
        );
    }

    @Test
    @DisplayName("다른 Member의 커서는 거절한다")
    void parseOtherMember_failure() {
        // given
        String encoded = SearchConversationCursor.of(
                1,
                2,
                TIME,
                301
        )
                .encode();

        // when & then
        assertInvalidCursor(
                encoded,
                1,
                3
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "%%%", "MQ", "%%%invalid"})
    @DisplayName("빈 값과 손상된 인코딩은 INVALID_PARAMETER다")
    void parseInvalidEncoding_failure(String encoded) {
        // when & then
        assertInvalidCursor(
                encoded,
                1,
                2
        );
    }

    @Test
    @DisplayName("과도하게 긴 커서는 디코딩 전에 거절한다")
    void parseOversized_failure() {
        // when & then
        assertInvalidCursor(
                "a".repeat(513),
                1,
                2
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"OTHER|1|2|2026-10-10T00:00:00Z|301", "SC1|1|2|2026-10-10T00:00:00Z",
            "SC1|1|2|2026-10-10T00:00:00Z|301|extra", "SC1|0|2|2026-10-10T00:00:00Z|301",
            "SC1|1|-2|2026-10-10T00:00:00Z|301", "SC1|1|2|invalid|301", "SC1|1|2|2026-10-10T00:00:00Z|0",
            "SC1|1|2|2026-10-10T00:00:00Z|9223372036854775808", "SC1|1|2|+100000-01-01T00:00:00Z|301"})
    @DisplayName("버전·필드 수·식별자·시각이 잘못된 payload를 거절한다")
    void parseInvalidPayload_failure(String payload) {
        // given
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        // when & then
        assertInvalidCursor(
                encoded,
                1,
                2
        );
    }

    private void assertInvalidCursor(
            String encoded,
            long workspaceId,
            long memberId
    ) {
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> SearchConversationCursor.parse(
                        encoded,
                        workspaceId,
                        memberId
                )
        )
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(SearchErrorCode.INVALID_PARAMETER));
    }
}
