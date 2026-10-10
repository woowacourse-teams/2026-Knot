package com.knot.backend.global.infrastructure.llm;

public record LlmGenerationOptions(
        double temperature,
        double topP,
        int topK,
        double repeatPenalty,
        boolean thinkingEnabled,
        Integer thinkingBudgetTokens,
        int maxTokens
) {

}
