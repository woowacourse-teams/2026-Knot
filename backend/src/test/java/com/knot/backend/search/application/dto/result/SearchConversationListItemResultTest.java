package com.knot.backend.search.application.dto.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchConversationListItemResultTest {
    private static final Instant TIME = Instant.parse("2026-10-10T00:00:00Z");

    @Test
    @DisplayName("첫 질문과 마지막 메시지의 개행과 Unicode 공백을 한 칸으로 정리한다")
    void fromNormalizedText_success() {
        // when
        SearchConversationListItemResult result = SearchConversationListItemResult.from(
                1,
                "  첫\n질문\t내용\u00a0입니다  ",
                " 마지막\n 답변 ",
                TIME,
                TIME
        );

        // then
        assertThat(result.title()).isEqualTo("첫 질문 내용 입니다");
        assertThat(result.lastMessagePreview()).isEqualTo("마지막 답변");
    }

    @Test
    @DisplayName("제목 80자와 미리보기 120자를 Unicode code point 기준으로 제한한다")
    void fromUnicodeBoundary_success() {
        // given
        String question = "가".repeat(79) + "😀" + "끝";
        String answer = "나".repeat(119) + "😀" + "끝";

        // when
        SearchConversationListItemResult result = SearchConversationListItemResult.from(
                1,
                question,
                answer,
                TIME,
                TIME
        );

        // then
        assertThat(result.title()).isEqualTo("가".repeat(79) + "😀");
        assertThat(result.lastMessagePreview()).isEqualTo("나".repeat(119) + "😀");
        assertThat(question).endsWith("끝");
        assertThat(answer).endsWith("끝");
    }

    @Test
    @DisplayName("빈 STREAMING 답변은 빈 미리보기이며 질문으로 대체하지 않는다")
    void fromEmptyAnswer_success() {
        // when
        SearchConversationListItemResult result = SearchConversationListItemResult.from(
                1,
                "질문",
                "",
                TIME,
                TIME
        );

        // then
        assertThat(result.lastMessagePreview()).isEmpty();
    }

    @Test
    @DisplayName("마지막 메시지가 없으면 null 미리보기를 유지한다")
    void fromMissingMessage_success() {
        // when
        SearchConversationListItemResult result = SearchConversationListItemResult.from(
                1,
                "질문",
                null,
                TIME,
                TIME
        );

        // then
        assertThat(result.lastMessagePreview()).isNull();
    }
}
