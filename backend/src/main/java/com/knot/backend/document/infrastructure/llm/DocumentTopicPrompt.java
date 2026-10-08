package com.knot.backend.document.infrastructure.llm;

import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
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
public class DocumentTopicPrompt {

    private final ObjectMapper mapper;
    private final String systemPrompt;
    private final JsonNode schema;

    public DocumentTopicPrompt(ObjectMapper mapper) {
        this.mapper = mapper;
        systemPrompt = readResource("llm/document/topic-classification-system.txt");
        schema = mapper.readTree(readResource("llm/document/topic-classification-schema.json"));
    }

    public LlmCompletionRequest createRequest(String transcriptContent) {
        validateTranscriptContent(transcriptContent);
        return new LlmCompletionRequest(
                createMessages(transcriptContent),
                "document_topics",
                schema,
                new LlmGenerationOptions(
                        0.2,
                        0.9,
                        20,
                        1.0,
                        false,
                        null,
                        1024
                )
        );
    }

    private void validateTranscriptContent(String transcriptContent) {
        if (transcriptContent == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_INPUT);
        }
    }

    private List<LlmMessage> createMessages(String transcriptContent) {
        return List.of(
                new LlmMessage(
                        "system",
                        systemPrompt
                ),
                new LlmMessage(
                        "user",
                        serializeTranscript(transcriptContent)
                )
        );
    }

    private String serializeTranscript(String transcriptContent) {
        return mapper.writeValueAsString(
                mapper.createObjectNode()
                        .put(
                                "transcriptText",
                                transcriptContent
                        )
        );
    }

    private String readResource(String path) {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException | JacksonException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_INPUT);
        }
    }
}
