package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentListQuery;
import com.knot.backend.document.application.dto.query.DocumentListParameters;
import com.knot.backend.document.application.dto.result.DocumentCardResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.application.dto.result.DocumentTopicResult;
import com.knot.backend.document.domain.DocumentCursor;
import com.knot.backend.document.domain.MyConfirmationState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentListQueryAdapter implements DocumentListQuery {
    private final DocumentListJpaRepository documents;

    @Override
    public List<DocumentTopicResult> findTopics(
            long workspaceId,
            long memberId,
            DocumentListParameters parameters
    ) {
        return documents.findTopics(
                workspaceId,
                memberId,
                parameters.recordingSessionId(),
                confirmationFilter(parameters.myConfirmation())
        )
                .stream()
                .map(
                        row -> new DocumentTopicResult(
                                row.topic(),
                                Math.toIntExact(row.documentCount())
                        )
                )
                .toList();
    }

    @Override
    public List<DocumentCardResult> findPage(
            long workspaceId,
            long memberId,
            DocumentListParameters parameters,
            DocumentCursor cursor
    ) {
        List<DocumentCardRow> cards = documents.findPage(
                workspaceId,
                memberId,
                parameters.recordingSessionId(),
                confirmationFilter(parameters.myConfirmation()),
                cursor != null,
                cursorTime(cursor),
                cursorId(cursor),
                PageRequest.of(
                        0,
                        parameters.size() + 1
                )
        );
        if (cards.isEmpty()) {
            return List.of();
        }
        List<Long> documentIds = cards.stream()
                .map(DocumentCardRow::id)
                .toList();
        Map<Long, DocumentCardCountsRow> counts = documents.findCounts(
                workspaceId,
                documentIds
        )
                .stream()
                .collect(
                        Collectors.toMap(
                                DocumentCardCountsRow::documentId,
                                Function.identity()
                        )
                );
        return cards.stream()
                .map(
                        card -> toResult(
                                card,
                                counts.get(card.id())
                        )
                )
                .toList();
    }

    private String confirmationFilter(MyConfirmationState state) {
        if (state == null) {
            return "ALL";
        }
        return state.name();
    }

    private Instant cursorTime(DocumentCursor cursor) {
        if (cursor == null) {
            return Instant.EPOCH;
        }
        return cursor.getCreatedAt();
    }

    private long cursorId(DocumentCursor cursor) {
        if (cursor == null) {
            return 0;
        }
        return cursor.getDocumentId();
    }

    private DocumentCardResult toResult(
            DocumentCardRow row,
            DocumentCardCountsRow counts
    ) {
        return new DocumentCardResult(
                row.id(),
                row.recordingSessionId(),
                row.topic(),
                row.title(),
                row.summary(),
                row.status(),
                row.createdAt(),
                Math.toIntExact(row.recordingDurationMillis() / 1000),
                MyConfirmationState.resolve(
                        row.requiredByMe(),
                        row.confirmedAtByMe()
                ),
                new DocumentConfirmationSummaryResult(
                        Math.toIntExact(counts.confirmedCount()),
                        Math.toIntExact(counts.pendingCount()),
                        Math.toIntExact(counts.excludedCount())
                )
        );
    }
}
