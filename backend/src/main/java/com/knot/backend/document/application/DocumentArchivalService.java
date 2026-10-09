package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.domain.Document;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentArchivalService {

    private final DocumentRepository documents;
    private final DocumentConfirmationQuery query;

    @Transactional(propagation = Propagation.MANDATORY)
    public void archiveAfterMemberDeparture(
            long workspaceId,
            long memberId,
            Instant leftAt
    ) {
        for (Document document : documents.findAffectedDraftsForUpdate(
                workspaceId,
                memberId
        )) {
            DocumentConfirmationSummaryResult summary = query.findSummary(
                    workspaceId,
                    document.getId(),
                    memberId
            )
                    .orElseThrow(() -> new DocumentException(DocumentErrorCode.DOCUMENT_NOT_FOUND))
                    .summary();
            if (summary.pendingCount() == 0) {
                document.archive(leftAt);
            }
        }
        documents.flush();
    }
}
