package com.knot.backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatSearchQueryComposerTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    @DisplayName("이전 메시지가 없으면 현재 질문을 그대로 검색 질의로 쓴다")
    void compose_success_noHistory() {
        // given
        List<ChatMessage> previousMessages = List.of();

        // when
        String query = ChatSearchQueryComposer.compose(
                "PostgreSQL을 왜 썼지?",
                previousMessages
        );

        // then
        assertThat(query).isEqualTo("PostgreSQL을 왜 썼지?");
    }

    @Test
    @DisplayName("직전 4개 메시지만 역할과 함께 붙이고 마지막 줄에 현재 질문을 둔다")
    void compose_success_lastFourMessages() {
        // given
        List<ChatMessage> previousMessages = List.of(
                message(
                        ChatMessageRole.USER,
                        "오래된 질문"
                ),
                message(
                        ChatMessageRole.ASSISTANT,
                        "오래된 답변"
                ),
                message(
                        ChatMessageRole.USER,
                        "DB 뭐 써?"
                ),
                message(
                        ChatMessageRole.ASSISTANT,
                        "PostgreSQL을 씁니다"
                ),
                message(
                        ChatMessageRole.USER,
                        "언제 정했어?"
                ),
                message(
                        ChatMessageRole.ASSISTANT,
                        "8월 회의에서 정했습니다"
                )
        );

        // when
        String query = ChatSearchQueryComposer.compose(
                "왜 그렇게 정했어?",
                previousMessages
        );

        // then
        assertThat(query).isEqualTo("""
                USER: DB 뭐 써?
                ASSISTANT: PostgreSQL을 씁니다
                USER: 언제 정했어?
                ASSISTANT: 8월 회의에서 정했습니다
                현재 질문: 왜 그렇게 정했어?""");
    }

    @Test
    @DisplayName("이전 맥락이 길면 앞부분을 잘라 전체를 4,000자에 맞춘다")
    void compose_success_truncatesOldContext() {
        // given
        List<ChatMessage> previousMessages = List.of(
                message(
                        ChatMessageRole.USER,
                        "가".repeat(5000)
                )
        );

        // when
        String query = ChatSearchQueryComposer.compose(
                "질문",
                previousMessages
        );

        // then
        assertThat(query).hasSize(ChatSearchQueryComposer.MAX_SEARCH_QUERY_CHARACTERS)
                .endsWith("\n현재 질문: 질문")
                .doesNotStartWith("USER:");
    }

    @Test
    @DisplayName("현재 질문만으로 상한을 채우면 이전 맥락 없이 질문만 쓴다")
    void compose_success_longQuestionOnly() {
        // given
        String longQuestion = "나".repeat(ChatSearchQueryComposer.MAX_SEARCH_QUERY_CHARACTERS);
        List<ChatMessage> previousMessages = List.of(
                message(
                        ChatMessageRole.USER,
                        "이전 질문"
                )
        );

        // when
        String query = ChatSearchQueryComposer.compose(
                longQuestion,
                previousMessages
        );

        // then
        assertThat(query).isEqualTo(longQuestion);
    }

    private static ChatMessage message(
            ChatMessageRole role,
            String content
    ) {
        return ChatMessage.create(
                10L,
                role,
                content,
                CREATED_AT
        );
    }
}
