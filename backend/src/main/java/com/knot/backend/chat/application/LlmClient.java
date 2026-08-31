package com.knot.backend.chat.application;

import com.knot.backend.chat.application.dto.command.LlmRequest;

public interface LlmClient {

    LlmStream start(LlmRequest request);
}
