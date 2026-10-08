package com.knot.backend.global.infrastructure.llm;

import java.util.List;
import tools.jackson.databind.JsonNode;

public record LlmCompletionRequest(
        List<LlmMessage> messages,
        String schemaName,
        JsonNode outputSchema,
        LlmGenerationOptions options
) {

    public LlmCompletionRequest {
        if (messages != null) {
            messages = List.copyOf(messages);
        }
        if (outputSchema != null) {
            outputSchema = outputSchema.deepCopy();
        }
    }

    @Override
    public String toString() {
        return "LlmCompletionRequest[schemaName=" + schemaName + ", messages=REDACTED]";
    }
}
