package com.knot.backend.chat.application.dto.result;

/** 턴 저장 API가 돌려주는 메시지 ID 한 쌍(기획서 6.4). 출처는 {@code GET /messages/{messageId}/sources}로 조회한다. */
public record ChatTurnResult(
        long userMessageId,
        long messageId
) {

    public static ChatTurnResult from(ChatTurn turn) {
        return new ChatTurnResult(
                turn.userMessage()
                        .getId(),
                turn.assistantMessage()
                        .getId()
        );
    }
}
