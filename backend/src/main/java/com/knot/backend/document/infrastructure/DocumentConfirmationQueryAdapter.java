package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentConfirmationQuery;
import com.knot.backend.document.application.dto.result.DocumentConfirmationItemResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationOverviewResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.domain.DocumentConfirmationCursor;
import com.knot.backend.document.domain.DocumentConfirmationState;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentConfirmationQueryAdapter implements DocumentConfirmationQuery {
    private final DocumentConfirmationReadJpaRepository confirmations;

    @Override
    public Optional<DocumentConfirmationOverviewResult> findSummary(
            long workspaceId,
            long documentId,
            long memberId
    ) {
        return confirmations.findSummary(
                workspaceId,
                documentId,
                memberId
        )
                .map(this::toOverview);
    }

    @Override
    public List<DocumentConfirmationItemResult> findPage(
            long workspaceId,
            long documentId,
            int size,
            DocumentConfirmationCursor cursor
    ) {
        return confirmations.findPage(
                workspaceId,
                documentId,
                DocumentConfirmationState.CONFIRMED.getSortOrder(),
                DocumentConfirmationState.PENDING.getSortOrder(),
                DocumentConfirmationState.EXCLUDED.getSortOrder(),
                cursor != null,
                cursorOrder(cursor),
                cursorMemberId(cursor),
                PageRequest.of(
                        0,
                        size + 1
                )
        )
                .stream()
                .map(this::toItem)
                .toList();
    }

    private int cursorOrder(DocumentConfirmationCursor cursor) {
        if (cursor == null) {
            return DocumentConfirmationState.CONFIRMED.getSortOrder();
        }
        return cursor.getState()
                .getSortOrder();
    }

    private long cursorMemberId(DocumentConfirmationCursor cursor) {
        if (cursor == null) {
            return 0;
        }
        return cursor.getTargetMemberId();
    }

    private DocumentConfirmationOverviewResult toOverview(DocumentConfirmationSummaryRow row) {
        return new DocumentConfirmationOverviewResult(
                row.documentId(),
                new DocumentConfirmationSummaryResult(
                        Math.toIntExact(row.confirmedCount()),
                        Math.toIntExact(row.pendingCount()),
                        Math.toIntExact(row.excludedCount())
                ),
                row.confirmedByMe()
        );
    }

    private DocumentConfirmationItemResult toItem(DocumentConfirmationTargetRow row) {
        return new DocumentConfirmationItemResult(
                row.memberId(),
                row.nickname(),
                row.profileImageUrl(),
                row.confirmedAt(),
                DocumentConfirmationState.resolve(
                        row.activeMember(),
                        row.confirmedAt()
                )
        );
    }
}
