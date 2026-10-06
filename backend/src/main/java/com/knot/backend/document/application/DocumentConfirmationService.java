package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.query.DocumentConfirmationParameters;
import com.knot.backend.document.application.dto.result.DocumentConfirmationItemResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationOverviewResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationsResult;
import com.knot.backend.document.domain.DocumentConfirmationCursor;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentConfirmationService {
    private final WorkspaceMemberRepository workspaceMembers;
    private final DocumentConfirmationQuery query;

    public DocumentConfirmationsResult find(
            long workspaceId,
            long memberId,
            long documentId,
            DocumentConfirmationParameters parameters
    ) {
        validateWorkspaceAccess(
                workspaceId,
                memberId
        );
        DocumentConfirmationCursor cursor = parseCursor(
                workspaceId,
                documentId,
                memberId,
                parameters.cursor()
        );
        DocumentConfirmationOverviewResult overview = query.findSummary(
                workspaceId,
                documentId,
                memberId
        )
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
        List<DocumentConfirmationItemResult> page = query.findPage(
                workspaceId,
                documentId,
                parameters.size(),
                cursor
        );
        return new DocumentConfirmationsResult(
                overview.documentId(),
                overview.summary(),
                overview.confirmedByMe(),
                limitPageItems(
                        page,
                        parameters.size()
                ),
                createNextCursor(
                        workspaceId,
                        documentId,
                        memberId,
                        page,
                        parameters.size()
                )
        );
    }

    private void validateWorkspaceAccess(
            long workspaceId,
            long memberId
    ) {
        if (!workspaceMembers.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }

    private DocumentConfirmationCursor parseCursor(
            long workspaceId,
            long documentId,
            long memberId,
            String cursor
    ) {
        if (cursor == null) {
            return null;
        }
        return DocumentConfirmationCursor.parse(
                cursor,
                workspaceId,
                documentId,
                memberId
        );
    }

    private List<DocumentConfirmationItemResult> limitPageItems(
            List<DocumentConfirmationItemResult> page,
            int size
    ) {
        if (page.size() > size) {
            return page.subList(
                    0,
                    size
            );
        }
        return page;
    }

    private String createNextCursor(
            long workspaceId,
            long documentId,
            long memberId,
            List<DocumentConfirmationItemResult> page,
            int size
    ) {
        if (page.size() <= size) {
            return null;
        }
        DocumentConfirmationItemResult last = page.get(size - 1);
        return DocumentConfirmationCursor.of(
                workspaceId,
                documentId,
                memberId,
                last.state(),
                last.memberId()
        )
                .encode();
    }
}
