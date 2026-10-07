package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentConfirmationResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.domain.Document;
import com.knot.backend.document.domain.DocumentConfirmation;
import com.knot.backend.document.domain.DocumentConfirmationRepository;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentRepository;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentConfirmationCommandService {
    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final DocumentRepository documents;
    private final DocumentConfirmationRepository confirmations;
    private final DocumentConfirmationQuery query;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DocumentConfirmationResult confirm(
            long workspaceId,
            long memberId,
            long documentId
    ) {
        lockWorkspaceAndValidateAccess(
                workspaceId,
                memberId
        );
        Document document = documents.findByWorkspaceIdAndIdForUpdate(
                workspaceId,
                documentId
        )
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
        DocumentConfirmation confirmation = requireConfirmationTarget(
                documentId,
                memberId
        );
        Instant confirmedAt = Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
        confirmation.confirm(confirmedAt);
        confirmations.flush();
        DocumentConfirmationSummaryResult summary = query.findSummary(
                workspaceId,
                documentId,
                memberId
        )
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.DOCUMENT_NOT_FOUND))
                .summary();
        archiveIfNoPendingTargets(
                document,
                summary,
                confirmedAt
        );
        documents.flush();
        return new DocumentConfirmationResult(
                documentId,
                confirmation.getConfirmedAt(),
                document.getStatus(),
                document.getArchivedAt(),
                summary
        );
    }

    private void lockWorkspaceAndValidateAccess(
            long workspaceId,
            long memberId
    ) {
        workspaces.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));
        if (!members.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }

    private DocumentConfirmation requireConfirmationTarget(
            long documentId,
            long memberId
    ) {
        return confirmations.findByDocumentIdAndMemberId(
                documentId,
                memberId
        )
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.CONFIRMATION_NOT_REQUIRED));
    }

    private void archiveIfNoPendingTargets(
            Document document,
            DocumentConfirmationSummaryResult summary,
            Instant archivedAt
    ) {
        if (summary.pendingCount() == 0) {
            document.archive(archivedAt);
        }
    }
}
