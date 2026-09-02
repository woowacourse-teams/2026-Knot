package com.knot.backend.workspace.domain;

import java.time.Instant;
import java.util.List;

public interface ImportedPageRepository {

    ImportedPage save(ImportedPage importedPage);

    long countByWorkspaceIdAndImportRunId(
            Long workspaceId,
            Long importRunId
    );

    List<ImportedPage> findAllByWorkspaceIdAndImportRunIdOrderByPositionAscIdAsc(
            Long workspaceId,
            Long importRunId
    );

    void publish(
            Long workspaceId,
            Long importRunId,
            Instant publishedAt
    );

    List<ImportedPageMetadata> findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId);
}
