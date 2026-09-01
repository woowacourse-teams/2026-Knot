package com.knot.backend.workspace.domain;

import java.util.List;
import java.time.Instant;

public interface NotionPageRepository {

    NotionPage save(NotionPage notionPage);

    long countByWorkspaceIdAndImportRunId(
            Long workspaceId,
            Long importRunId
    );

    void publish(
            Long workspaceId,
            Long importRunId,
            Instant publishedAt
    );

    List<NotionPageMetadata> findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId);
}
