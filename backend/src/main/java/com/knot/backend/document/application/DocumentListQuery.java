package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.query.DocumentListParameters;
import com.knot.backend.document.application.dto.result.DocumentCardResult;
import com.knot.backend.document.application.dto.result.DocumentTopicResult;
import com.knot.backend.document.domain.DocumentCursor;
import java.util.List;

public interface DocumentListQuery {

    List<DocumentTopicResult> findTopics(
            long workspaceId,
            long memberId,
            DocumentListParameters parameters
    );

    List<DocumentCardResult> findPage(
            long workspaceId,
            long memberId,
            DocumentListParameters parameters,
            DocumentCursor cursor
    );
}
