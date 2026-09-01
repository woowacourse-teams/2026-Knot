package com.knot.backend.workspace.domain;

public record NotionPageMetadata(
        Long id,
        Long workspaceId,
        Long parentPageId,
        String title,
        int position,
        String notionUrl
) {
}
