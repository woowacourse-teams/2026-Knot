package com.knot.backend.chat.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "탐색 검색 요청")
public record SearchChatMessageRequest(
        @Schema(description = "사용자 질문", example = "PostgreSQL을 왜 사용했나요?", maxLength = 10_000) @NotBlank(message = "메시지는 비어 있을 수 없습니다") @Size(max = 10_000, message = "메시지는 10000자 이하여야 합니다") String content
) {
}
