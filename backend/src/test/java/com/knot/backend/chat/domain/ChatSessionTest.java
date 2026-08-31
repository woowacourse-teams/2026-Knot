package com.knot.backend.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatSessionTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    @DisplayName("제목이 없으면 새 대화 제목으로 세션을 생성한다")
    void create_success_defaultTitle() {
        // given

        // when
        ChatSession chatSession = ChatSession.create(
                1L,
                2L,
                null,
                CREATED_AT
        );

        // then
        assertThat(chatSession.getTitle()).isEqualTo(ChatSession.DEFAULT_TITLE);
        assertThat(chatSession.getLastMessageAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("메시지 시각이 더 늦으면 마지막 메시지 시각을 갱신한다")
    void updateLastMessageAt_success() {
        // given
        ChatSession chatSession = ChatSession.create(
                1L,
                2L,
                "질문",
                CREATED_AT
        );
        Instant messageCreatedAt = CREATED_AT.plusSeconds(1);

        // when
        chatSession.updateLastMessageAt(messageCreatedAt);

        // then
        assertThat(chatSession.getLastMessageAt()).isEqualTo(messageCreatedAt);
    }

    @Test
    @DisplayName("세션 제목이 최대 길이를 넘으면 생성을 거부한다")
    void create_failure_titleTooLong() {
        // given
        String tooLongTitle = "가".repeat(ChatSession.MAX_TITLE_LENGTH + 1);

        // when
        ThrowingCallable action = () -> ChatSession.create(
                1L,
                2L,
                tooLongTitle,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.INVALID_CHAT_SESSION_TITLE);
    }

    @Test
    @DisplayName("워크스페이스 ID가 양수가 아니면 세션 생성을 거부한다")
    void create_failure_invalidWorkspaceId() {
        // given

        // when
        ThrowingCallable action = () -> ChatSession.create(
                0L,
                2L,
                null,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.INVALID_CHAT_SESSION_WORKSPACE_ID);
    }
}
