package com.knot.backend.document.infrastructure.llm;

import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.global.infrastructure.llm.LlmClient;
import com.knot.backend.global.infrastructure.llm.LlmCompletionRequest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "knot.llm", name = "enabled", havingValue = "true")
public class DocumentTopicClassifier {
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{javaWhitespace}\\p{Z}]+");

    private final DocumentTopicPrompt prompt;
    private final LlmClient client;
    private final ObjectMapper mapper;

    public DocumentTopicClassifier(
            DocumentTopicPrompt prompt,
            LlmClient client,
            ObjectMapper mapper
    ) {
        this.prompt = prompt;
        this.client = client;
        this.mapper = mapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
    }

    public List<String> classify(String transcriptContent) {
        LlmCompletionRequest request = prompt.createRequest(transcriptContent);
        String response = client.complete(request);
        return parseTopics(response);
    }

    private List<String> parseTopics(String response) {
        JsonNode root = readResponse(response);
        validateTopicsArray(root);
        List<String> topics = new ArrayList<>();
        for (JsonNode topic : root.path("topics")) {
            validateTopicName(topic);
            String topicName = topic.asString();
            String normalizedTopicName = normalizeTopicName(topicName);
            topics.add(normalizedTopicName);
        }
        return removeDuplicateTopics(topics);
    }

    private JsonNode readResponse(String response) {
        try (JsonParser parser = mapper.createParser(response)) {
            JsonNode root = mapper.readTree(parser);
            if (parser.nextToken() != null) {
                throw invalidResponse();
            }
            return root;
        } catch (JacksonException exception) {
            throw invalidResponse();
        }
    }

    private void validateTopicsArray(JsonNode root) {
        validateResponseObject(root);
        if (root.size() != 1) {
            throw invalidResponse();
        }
        JsonNode topics = root.path("topics");
        if (!topics.isArray()) {
            throw invalidResponse();
        }
    }

    private void validateResponseObject(JsonNode root) {
        if (root == null) {
            throw invalidResponse();
        }
        if (!root.isObject()) {
            throw invalidResponse();
        }
    }

    private void validateTopicName(JsonNode topic) {
        if (!topic.isString()) {
            throw invalidResponse();
        }
    }

    private String normalizeTopicName(String topic) {
        String normalized = Normalizer.normalize(
                topic,
                Normalizer.Form.NFC
        );
        normalized = WHITESPACE.matcher(normalized)
                .replaceAll(" ")
                .strip();
        if (normalized.isEmpty()) {
            throw invalidResponse();
        }
        return normalized;
    }

    private List<String> removeDuplicateTopics(List<String> topics) {
        return List.copyOf(new LinkedHashSet<>(topics));
    }

    private DocumentException invalidResponse() {
        return new DocumentException(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_RESPONSE);
    }
}
