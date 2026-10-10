package com.knot.backend.global.infrastructure.llm;

public record LlmMessage(
        String role,
        String content
) {

    @Override
    public String toString() {
        return "LlmMessage[role=" + role + ", content=REDACTED]";
    }
}
