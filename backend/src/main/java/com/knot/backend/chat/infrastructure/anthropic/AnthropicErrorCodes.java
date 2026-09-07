package com.knot.backend.chat.infrastructure.anthropic;

import com.knot.backend.chat.domain.ChatErrorCode;

final class AnthropicErrorCodes {

    private AnthropicErrorCodes() {}

    static ChatErrorCode forStatus(int status) {
        return switch (status) {
            case 401, 403 -> ChatErrorCode.LLM_CONFIGURATION_INVALID;
            case 429, 529 -> ChatErrorCode.LLM_RATE_LIMITED;
            default -> ChatErrorCode.LLM_STREAM_FAILED;
        };
    }

    static ChatErrorCode forErrorType(String errorType) {
        return switch (errorType) {
            case "authentication_error", "permission_error" -> ChatErrorCode.LLM_CONFIGURATION_INVALID;
            case "rate_limit_error", "overloaded_error" -> ChatErrorCode.LLM_RATE_LIMITED;
            default -> ChatErrorCode.LLM_STREAM_FAILED;
        };
    }
}
