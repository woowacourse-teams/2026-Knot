package com.knot.backend.chat.application.dto.command;

import java.util.List;

public record LlmRequest(List<LlmMessage> messages) {

    public LlmRequest {
        messages = List.copyOf(messages);
    }
}
