package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentConfirmationOverviewResult;
import java.util.Optional;

public interface DocumentConfirmationQuery {

    Optional<DocumentConfirmationOverviewResult> findSummary(
            long workspaceId,
            long documentId,
            long memberId
    );

}
