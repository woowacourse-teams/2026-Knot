package com.knot.backend.document.application;

import com.knot.backend.document.domain.DocumentText;
import com.knot.backend.document.application.dto.result.DocumentTranscriptResult;
import com.knot.backend.document.application.dto.result.DocumentTranscriptSnapshot;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DocumentTranscriptService {

    private final WorkspaceMemberRepository members;
    private final DocumentTranscriptQuery query;

    public DocumentTranscriptResult find(
            long workspaceId,
            long memberId,
            long documentId
    ) {
        validateIdentifiers(
                workspaceId,
                memberId,
                documentId
        );
        validateWorkspaceAccess(
                workspaceId,
                memberId
        );
        DocumentTranscriptSnapshot snapshot = query.find(
                workspaceId,
                documentId
        )
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.TRANSCRIPT_NOT_FOUND));
        validateStoredTranscript(snapshot);
        return DocumentTranscriptResult.from(snapshot);
    }

    private void validateIdentifiers(
            long workspaceId,
            long memberId,
            long documentId
    ) {
        if (workspaceId <= 0 || memberId <= 0 || documentId <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private void validateWorkspaceAccess(
            long workspaceId,
            long memberId
    ) {
        if (!members.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }

    private void validateStoredTranscript(DocumentTranscriptSnapshot snapshot) {
        if (DocumentText.isBlank(snapshot.transcriptText())) {
            throw new DocumentException(DocumentErrorCode.INVALID_TRANSCRIPT_DATA);
        }
        if (snapshot.segments()
                .isEmpty()) {
            throw new DocumentException(DocumentErrorCode.INVALID_TRANSCRIPT_DATA);
        }
    }
}
