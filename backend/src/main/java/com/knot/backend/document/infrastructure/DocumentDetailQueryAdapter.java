package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentDetailQuery;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.application.dto.result.DocumentDetailSnapshot;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentDetailQueryAdapter implements DocumentDetailQuery {
    private final DocumentReadJpaRepository documents;

    @Override
    public Optional<DocumentDetailSnapshot> find(
            long workspaceId,
            long documentId,
            long memberId
    ) {
        return documents.findDetail(
                workspaceId,
                documentId,
                memberId
        )
                .map(this::toSnapshot);
    }

    private DocumentDetailSnapshot toSnapshot(DocumentDetailRow row) {
        return new DocumentDetailSnapshot(
                row.id(),
                row.recordingSessionId(),
                row.topic(),
                row.title(),
                row.summary(),
                row.content(),
                row.status(),
                row.createdAt(),
                row.archivedAt(),
                row.recordingDurationMillis(),
                row.sourceTranscriptId(),
                row.requiredByMe(),
                row.confirmedAtByMe(),
                new DocumentConfirmationSummaryResult(
                        Math.toIntExact(row.confirmedCount()),
                        Math.toIntExact(row.pendingCount()),
                        Math.toIntExact(row.excludedCount())
                )
        );
    }
}
