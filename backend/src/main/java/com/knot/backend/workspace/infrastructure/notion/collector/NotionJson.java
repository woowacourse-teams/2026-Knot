package com.knot.backend.workspace.infrastructure.notion.collector;

import com.knot.backend.workspace.application.NotionCollectionException;
import com.knot.backend.workspace.application.NotionCollectionFailureType;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

final class NotionJson {
    private static final Pattern NOTION_ID = Pattern.compile("[A-Za-z0-9-]{1,100}");

    private NotionJson() {}

    static String requiredNotionId(
            JsonNode node,
            String fieldName
    ) {
        String id = requiredNonBlankString(
                node,
                fieldName
        );
        if (!NOTION_ID.matcher(id)
                .matches()) {
            throw invalidResponse();
        }
        return id;
    }

    static String requiredNonBlankString(
            JsonNode node,
            String fieldName
    ) {
        requireObject(node);
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isString() || value.asString()
                .isBlank()) {
            throw invalidResponse();
        }
        return value.asString();
    }

    static void requireObject(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw invalidResponse();
        }
    }

    static void requireArray(JsonNode value) {
        if (value == null || !value.isArray()) {
            throw invalidResponse();
        }
    }

    static NotionCollectionException invalidResponse() {
        return new NotionCollectionException(NotionCollectionFailureType.INVALID_RESPONSE);
    }
}
