package com.knot.backend.workspace.application.dto.result;

public record CollectedNotionPage(
        String notionPageId,
        String parentNotionPageId,
        String title,
        String markdownContent,
        int position,
        String notionUrl
) {
}
