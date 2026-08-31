package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.NotionPage;

public record NotionPageTreeItemResult(
        long id,
        Long parentPageId,
        String title,
        int position,
        String notionUrl
) {

    public static NotionPageTreeItemResult from(NotionPage notionPage) {
        return new NotionPageTreeItemResult(
                notionPage.getId(),
                notionPage.getParentPageId(),
                notionPage.getTitle(),
                notionPage.getPosition(),
                notionPage.getNotionUrl()
        );
    }
}
