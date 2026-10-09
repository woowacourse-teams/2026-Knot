package com.knot.backend.document.domain;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    Optional<Document> findByWorkspaceIdAndIdForUpdate(
            long workspaceId,
            long documentId
    );

    List<Document> findAffectedDraftsForUpdate(
            long workspaceId,
            long memberId
    );

    void flush();
}
