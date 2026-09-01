package com.knot.backend.workspace.domain;

public record ImportedPageMetadata(
        Long id,
        Long workspaceId,
        Long parentId,
        String title,
        int position,
        String sourceUrl
) {
}
