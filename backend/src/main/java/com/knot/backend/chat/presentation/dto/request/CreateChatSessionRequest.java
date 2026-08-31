package com.knot.backend.chat.presentation.dto.request;

import jakarta.validation.constraints.Size;

public record CreateChatSessionRequest(@Size(max = 255, message = "제목은 255자 이하여야 합니다") String title) {
}
