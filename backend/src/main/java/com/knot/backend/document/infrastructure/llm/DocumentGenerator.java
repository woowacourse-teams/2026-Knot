package com.knot.backend.document.infrastructure.llm;

import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.global.infrastructure.llm.LlmClient;
import com.knot.backend.global.infrastructure.llm.LlmCompletionRequest;
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
public class DocumentGenerator {

    private static final Pattern BLANK = Pattern.compile("[\\p{javaWhitespace}\\p{Z}]*");
    private static final Pattern OUTER_WHITESPACE = Pattern
            .compile("^[\\p{javaWhitespace}\\p{Z}]+|[\\p{javaWhitespace}\\p{Z}]+$");

    private final DocumentGenerationPrompt prompt;
    private final LlmClient client;
    private final ObjectMapper mapper;
    private final DocumentMarkdownValidator validator;

    public DocumentGenerator(
            DocumentGenerationPrompt prompt,
            LlmClient client,
            ObjectMapper mapper,
            DocumentMarkdownValidator validator
    ) {
        this.prompt = prompt;
        this.client = client;
        this.mapper = mapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.validator = validator;
    }

    public DocumentGenerationResult generate(
            String transcriptContent,
            String topic
    ) {
        LlmCompletionRequest request = prompt.createRequest(
                transcriptContent,
                topic
        );
        String response = client.complete(request);
        JsonNode root = readResponse(response);
        validateResponseFields(root);
        String title = stripOuterWhitespace(
                readRequiredText(
                        root,
                        "title"
                )
        );
        String summary = readSummary(root);
        String content = readRequiredText(
                root,
                "content"
        );
        DocumentGenerationResult result = new DocumentGenerationResult(
                title,
                summary,
                content
        );
        validator.validate(result);
        return result;
    }

    private JsonNode readResponse(String response) {
        if (response == null) {
            throw invalidResponse();
        }
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

    private void validateResponseFields(JsonNode root) {
        if (root == null || !root.isObject() || root.size() != 3) {
            throw invalidResponse();
        }
        if (!root.has("title") || !root.has("summary") || !root.has("content")) {
            throw invalidResponse();
        }
    }

    private String readRequiredText(
            JsonNode root,
            String field
    ) {
        JsonNode value = root.path(field);
        if (!value.isString()) {
            throw invalidResponse();
        }
        String text = value.asString();
        if (BLANK.matcher(text)
                .matches()) {
            throw invalidResponse();
        }
        return text;
    }

    private String readSummary(JsonNode root) {
        JsonNode summary = root.path("summary");
        if (summary.isNull()) {
            return null;
        }
        if (!summary.isString()) {
            throw invalidResponse();
        }
        String text = summary.asString();
        if (BLANK.matcher(text)
                .matches()) {
            return null;
        }
        return text;
    }

    private String stripOuterWhitespace(String value) {
        return OUTER_WHITESPACE.matcher(value)
                .replaceAll("");
    }

    private DocumentException invalidResponse() {
        return new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE);
    }
}
