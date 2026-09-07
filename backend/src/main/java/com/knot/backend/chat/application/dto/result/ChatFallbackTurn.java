package com.knot.backend.chat.application.dto.result;

import com.knot.backend.chat.domain.ChatMessage;

/** 근거 없음·넓은 질문일 때 한 트랜잭션으로 저장한 USER 질문과 서버 안내 ASSISTANT 답변(기획서 6.4). */
public record ChatFallbackTurn(
        ChatMessage userMessage,
        ChatMessage assistantMessage
) {
}
