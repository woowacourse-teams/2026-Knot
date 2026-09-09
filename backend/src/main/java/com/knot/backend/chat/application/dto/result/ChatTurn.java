package com.knot.backend.chat.application.dto.result;

import com.knot.backend.chat.domain.ChatMessage;

/**
 * 한 트랜잭션으로 저장한 USER 질문과 ASSISTANT 답변 한 쌍. 검색 API의 안내 답변(서버 생성)과 턴 저장 API의 에이전트
 * 답변(클라이언트 생성) 모두 이 모양이다(기획서 6.4).
 */
public record ChatTurn(
        ChatMessage userMessage,
        ChatMessage assistantMessage
) {
}
