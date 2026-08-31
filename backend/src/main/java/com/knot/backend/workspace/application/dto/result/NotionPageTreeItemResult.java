package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.NotionPageMetadata;

public record NotionPageTreeItemResult(
        long id,
        Long parentPageId,
        String title,
        int position,
        String notionUrl
) {

    public static NotionPageTreeItemResult from(NotionPageMetadata notionPage) {
        return new NotionPageTreeItemResult(
                notionPage.id(),
                notionPage.parentPageId(),
                notionPage.title(),
                notionPage.position(),
                notionPage.notionUrl()
        );
    }
}
