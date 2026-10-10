package com.knot.backend.search.application.dto.result;

import java.time.Instant;
import java.util.regex.Pattern;

public record SearchConversationListItemResult(
        long id,
        String title,
        String lastMessagePreview,
        Instant createdAt,
        Instant updatedAt
) {
    private static final int TITLE_LENGTH = 80;
    private static final int PREVIEW_LENGTH = 120;
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

    public static SearchConversationListItemResult from(
            long id,
            String firstQuestionContent,
            String lastMessageContent,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new SearchConversationListItemResult(
                id,
                displayText(
                        firstQuestionContent,
                        TITLE_LENGTH
                ),
                displayText(
                        lastMessageContent,
                        PREVIEW_LENGTH
                ),
                createdAt,
                updatedAt
        );
    }

    private static String displayText(
            String content,
            int maxLength
    ) {
        if (content == null) {
            return null;
        }
        String normalized = WHITESPACE.matcher(content)
                .replaceAll(" ")
                .strip();
        if (normalized.codePointCount(
                0,
                normalized.length()
        ) <= maxLength) {
            return normalized;
        }
        return normalized.substring(
                0,
                normalized.offsetByCodePoints(
                        0,
                        maxLength
                )
        );
    }
}
