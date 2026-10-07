package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.query.DocumentGenerationJobListParameters;
import com.knot.backend.document.application.dto.result.DocumentGenerationJobItemResult;
import com.knot.backend.document.application.dto.result.DocumentGenerationJobListResult;
import com.knot.backend.document.domain.DocumentGenerationJobCursor;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentGenerationJobListService {
    private final WorkspaceMemberRepository memberships;
    private final DocumentGenerationJobListQuery query;
    private final Clock clock;

    public DocumentGenerationJobListResult find(
            long workspaceId,
            long memberId,
            DocumentGenerationJobListParameters parameters
    ) {
        validateMembership(
                workspaceId,
                memberId
        );
        DocumentGenerationJobCursor cursor = parseCursor(
                parameters.cursor(),
                workspaceId,
                memberId
        );
        List<DocumentGenerationJobItemResult> fetched = query.findPage(
                workspaceId,
                parameters.size() + 1,
                cursor,
                clock.instant()
        );
        List<DocumentGenerationJobItemResult> items = limitItems(
                fetched,
                parameters.size()
        );
        return new DocumentGenerationJobListResult(
                items,
                nextCursor(
                        fetched,
                        items,
                        parameters.size(),
                        workspaceId,
                        memberId
                )
        );
    }

    private void validateMembership(
            long workspaceId,
            long memberId
    ) {
        if (!memberships.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }

    private DocumentGenerationJobCursor parseCursor(
            String encoded,
            long workspaceId,
            long memberId
    ) {
        if (encoded == null) {
            return null;
        }
        return DocumentGenerationJobCursor.parse(
                encoded,
                workspaceId,
                memberId
        );
    }

    private List<DocumentGenerationJobItemResult> limitItems(
            List<DocumentGenerationJobItemResult> fetched,
            int size
    ) {
        return List.copyOf(
                fetched.subList(
                        0,
                        Math.min(
                                size,
                                fetched.size()
                        )
                )
        );
    }

    private String nextCursor(
            List<DocumentGenerationJobItemResult> fetched,
            List<DocumentGenerationJobItemResult> items,
            int size,
            long workspaceId,
            long memberId
    ) {
        if (fetched.size() <= size) {
            return null;
        }
        DocumentGenerationJobItemResult last = items.getLast();
        return DocumentGenerationJobCursor.of(
                workspaceId,
                memberId,
                last.createdAt(),
                last.jobId()
        )
                .encode();
    }
}
