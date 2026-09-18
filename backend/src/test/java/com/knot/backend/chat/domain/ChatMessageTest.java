package com.knot.backend.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatMessageTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    @DisplayName("세션 ID, 역할, 내용으로 채팅 메시지를 생성한다")
    void create_success() {
        // given

        // when
        ChatMessage chatMessage = ChatMessage.create(
                1L,
                ChatMessageRole.USER,
                "질문 내용",
                CREATED_AT
        );

        // then
        assertThat(chatMessage.getSessionId()).isEqualTo(1L);
        assertThat(chatMessage.getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(chatMessage.getContent()).isEqualTo("질문 내용");
    }

    @Test
    @DisplayName("공백 내용이면 채팅 메시지 생성을 거부한다")
    void create_failure_blankContent() {
        // given

        // when
        ThrowingCallable action = () -> ChatMessage.create(
                1L,
                ChatMessageRole.USER,
                " ",
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.INVALID_CHAT_MESSAGE_CONTENT);
    }

    @Test
    @DisplayName("역할이 없으면 채팅 메시지 생성을 거부한다")
    void create_failure_missingRole() {
        // given

        // when
        ThrowingCallable action = () -> ChatMessage.create(
                1L,
                null,
                "질문 내용",
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.INVALID_CHAT_MESSAGE_ROLE);
    }
}
