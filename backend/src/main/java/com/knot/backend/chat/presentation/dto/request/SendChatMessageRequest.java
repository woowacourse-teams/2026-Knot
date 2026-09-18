package com.knot.backend.chat.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
        @NotBlank(message = "메시지는 비어 있을 수 없습니다") @Size(max = 10_000, message = "메시지는 10000자 이하여야 합니다") String content
) {
}
