package com.knot.backend.document.infrastructure.llm;

import com.knot.backend.document.domain.DocumentText;
import com.knot.backend.document.domain.DocumentGenerationInput;
import com.knot.backend.document.domain.DocumentTopic;
import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.exception.LlmException;
import com.knot.backend.global.infrastructure.llm.LlmCompletionRequest;
import com.knot.backend.global.infrastructure.llm.LlmGenerationOptions;
import com.knot.backend.global.infrastructure.llm.LlmMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "knot.llm", name = "enabled", havingValue = "true")
public class DocumentGenerationPrompt {

    private final ObjectMapper mapper;
    private final String systemPrompt;
    private final JsonNode schema;

    public DocumentGenerationPrompt(ObjectMapper mapper) {
        this.mapper = mapper;
        systemPrompt = readResource("generation-system.txt") + "\n[Markdown 템플릿]\n"
                + readResource("generation-template.md");
        schema = readSchema();
    }

    public LlmCompletionRequest createRequest(DocumentGenerationInput input) {
        DocumentTopic topic = input.getTopic();
        return new LlmCompletionRequest(
                createMessages(
                        input.getTranscriptContent(),
                        topic.value()
                ),
                "document_generation",
                schema,
                createOptions()
        );
    }

    private List<LlmMessage> createMessages(
            String transcriptContent,
            String topic
    ) {
        return List.of(
                new LlmMessage(
                        "system",
                        systemPrompt
                ),
                new LlmMessage(
                        "user",
                        serializeInput(
                                transcriptContent,
                                topic
                        )
                )
        );
    }

    private String serializeInput(
            String transcriptContent,
            String topic
    ) {
        return mapper.writeValueAsString(
                mapper.createObjectNode()
                        .put(
                                "transcriptText",
                                transcriptContent
                        )
                        .put(
                                "topic",
                                topic
                        )
        );
    }

    private LlmGenerationOptions createOptions() {
        return new LlmGenerationOptions(
                0.2,
                0.9,
                20,
                1.0,
                false,
                null,
                4096
        );
    }

    private JsonNode readSchema() {
        try {
            JsonNode result = mapper.readTree(readResource("generation-schema.json"));
            if (result == null || !result.isObject()) {
                throw new LlmException(LlmErrorCode.LLM_INVALID_CONFIGURATION);
            }
            return result;
        } catch (JacksonException exception) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_CONFIGURATION);
        }
    }

    private String readResource(String filename) {
        try (InputStream stream = new ClassPathResource("llm/document/" + filename).getInputStream()) {
            String value = new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
            if (DocumentText.isBlank(value)) {
                throw new LlmException(LlmErrorCode.LLM_INVALID_CONFIGURATION);
            }
            return value;
        } catch (IOException exception) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_CONFIGURATION);
        }
    }
}
