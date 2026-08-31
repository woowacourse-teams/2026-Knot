package com.knot.backend.chat.presentation.dto.response;

public record ChatErrorEvent(
        String code,
        String message
) {
}
