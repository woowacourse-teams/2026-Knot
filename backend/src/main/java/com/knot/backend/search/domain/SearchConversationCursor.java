package com.knot.backend.search.domain;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import lombok.Getter;

@Getter
public class SearchConversationCursor {
    private static final String VERSION = "SC1";
    private static final int FIELD_COUNT = 5;
    private static final int MAX_LENGTH = 512;

    private final long workspaceId;
    private final long memberId;
    private final Instant updatedAt;
    private final long conversationId;

    private SearchConversationCursor(
            long workspaceId,
            long memberId,
            Instant updatedAt,
            long conversationId
    ) {
        validateIdentifier(workspaceId);
        validateIdentifier(memberId);
        validateIdentifier(conversationId);
        validateUpdatedAt(updatedAt);
        this.workspaceId = workspaceId;
        this.memberId = memberId;
        this.updatedAt = updatedAt;
        this.conversationId = conversationId;
    }

    public static SearchConversationCursor of(
            long workspaceId,
            long memberId,
            Instant updatedAt,
            long conversationId
    ) {
        return new SearchConversationCursor(
                workspaceId,
                memberId,
                updatedAt,
                conversationId
        );
    }

    public String encode() {
        String payload = VERSION + "|" + workspaceId + "|" + memberId + "|" + updatedAt + "|" + conversationId;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static SearchConversationCursor parse(
            String encoded,
            long workspaceId,
            long memberId
    ) {
        try {
            validateEncodedCursor(encoded);
            String payload = new String(
                    Base64.getUrlDecoder()
                            .decode(encoded),
                    StandardCharsets.UTF_8
            );
            String[] fields = payload.split(
                    "\\|",
                    -1
            );
            validatePayload(fields);
            SearchConversationCursor cursor = of(
                    Long.parseLong(fields[1]),
                    Long.parseLong(fields[2]),
                    Instant.parse(fields[3]),
                    Long.parseLong(fields[4])
            );
            cursor.validateQueryContext(
                    workspaceId,
                    memberId
            );
            return cursor;
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateEncodedCursor(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
        if (encoded.length() > MAX_LENGTH) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validatePayload(String[] fields) {
        if (fields.length != FIELD_COUNT) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
        if (!VERSION.equals(fields[0])) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateIdentifier(long id) {
        if (id <= 0) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }

    private static void validateUpdatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
        int year = updatedAt.atOffset(ZoneOffset.UTC)
                .getYear();
        if (year < 1 || year > 9999) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }

    private void validateQueryContext(
            long workspaceId,
            long memberId
    ) {
        if (this.workspaceId != workspaceId || this.memberId != memberId) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }
}
