package com.knot.backend.global.infrastructure.llm;

public interface LlmClient {

    String complete(LlmCompletionRequest request);
}
