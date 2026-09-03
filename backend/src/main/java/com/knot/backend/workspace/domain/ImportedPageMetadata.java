package com.knot.backend.workspace.domain;

public record ImportedPageMetadata(
        Long id,
        Long workspaceId,
        Long parentId,
        boolean hasParentReference,
        String title,
        int position,
        String sourceUrl
) {
}
