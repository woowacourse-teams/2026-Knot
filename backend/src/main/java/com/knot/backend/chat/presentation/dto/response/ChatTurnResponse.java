package com.knot.backend.chat.presentation.dto.response;

import com.knot.backend.chat.application.dto.result.ChatTurnResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "턴 저장 응답. 출처는 GET /api/v1/conversations/messages/{messageId}/sources로 조회한다")
public record ChatTurnResponse(
        @Schema(description = "저장된 USER 메시지 ID", example = "101") long userMessageId,
        @Schema(description = "저장된 ASSISTANT 메시지 ID(generated_by=CLIENT)", example = "102") long messageId
) {

    public static ChatTurnResponse from(ChatTurnResult result) {
        return new ChatTurnResponse(
                result.userMessageId(),
                result.messageId()
        );
    }
}
