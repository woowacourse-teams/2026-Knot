package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.query.DocumentListParameters;
import com.knot.backend.document.application.dto.result.DocumentCardResult;
import com.knot.backend.document.application.dto.result.DocumentListResult;
import com.knot.backend.document.application.dto.result.DocumentTopicResult;
import com.knot.backend.document.domain.DocumentCursor;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DocumentListService {
    private final WorkspaceMemberRepository workspaceMembers;
    private final DocumentListQuery query;

    public DocumentListResult find(
            long workspaceId,
            long memberId,
            DocumentListParameters parameters
    ) {
        if (!workspaceMembers.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
        DocumentCursor cursor = parseCursor(
                workspaceId,
                memberId,
                parameters
        );
        List<DocumentTopicResult> topics = query.findTopics(
                workspaceId,
                memberId,
                parameters
        );
        List<DocumentCardResult> page = query.findPage(
                workspaceId,
                memberId,
                parameters,
                cursor
        );
        List<DocumentCardResult> items = limitPageItems(
                page,
                parameters.size()
        );
        String nextCursor = createNextCursor(
                workspaceId,
                memberId,
                parameters,
                page
        );
        return new DocumentListResult(
                topics,
                items,
                nextCursor
        );
    }

    private DocumentCursor parseCursor(
            long workspaceId,
            long memberId,
            DocumentListParameters parameters
    ) {
        if (parameters.cursor() == null) {
            return null;
        }
        return DocumentCursor.parse(
                parameters.cursor(),
                workspaceId,
                memberId,
                parameters.myConfirmation(),
                parameters.recordingSessionId()
        );
    }

    private List<DocumentCardResult> limitPageItems(
            List<DocumentCardResult> page,
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
            long memberId,
            DocumentListParameters parameters,
            List<DocumentCardResult> page
    ) {
        if (page.size() <= parameters.size()) {
            return null;
        }
        DocumentCardResult lastItem = page.get(parameters.size() - 1);
        return encodeCursor(
                workspaceId,
                memberId,
                parameters,
                lastItem
        );
    }

    private String encodeCursor(
            long workspaceId,
            long memberId,
            DocumentListParameters parameters,
            DocumentCardResult last
    ) {
        return DocumentCursor.of(
                workspaceId,
                memberId,
                parameters.myConfirmation(),
                parameters.recordingSessionId(),
                last.createdAt(),
                last.id()
        )
                .encode();
    }
}
