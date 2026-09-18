package com.knot.backend.workspace.application.dto.result;

public record CollectedPage(
        String externalPageId,
        String parentExternalPageId,
        String title,
        String markdownContent,
        int position,
        String sourceUrl
) {
}
