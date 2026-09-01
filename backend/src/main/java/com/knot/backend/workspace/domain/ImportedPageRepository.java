package com.knot.backend.workspace.domain;

import java.util.List;

public interface ImportedPageRepository {

    List<ImportedPageMetadata> findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId);
}
