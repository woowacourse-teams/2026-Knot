package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentConfirmationItemResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationOverviewResult;
import com.knot.backend.document.domain.DocumentConfirmationCursor;
import java.util.List;
import java.util.Optional;

public interface DocumentConfirmationQuery {

    Optional<DocumentConfirmationOverviewResult> findSummary(
            long workspaceId,
            long documentId,
            long memberId
    );

    List<DocumentConfirmationItemResult> findPage(
            long workspaceId,
            long documentId,
            int size,
            DocumentConfirmationCursor cursor
    );
}
