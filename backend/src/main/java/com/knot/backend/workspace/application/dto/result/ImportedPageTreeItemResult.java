package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.ImportedPageMetadata;

public record ImportedPageTreeItemResult(
        long id,
        Long parentId,
        String title,
        int position,
        String sourceUrl
) {

    public static ImportedPageTreeItemResult from(ImportedPageMetadata importedPage) {
        return new ImportedPageTreeItemResult(
                importedPage.id(),
                importedPage.parentId(),
                importedPage.title(),
                importedPage.position(),
                importedPage.sourceUrl()
        );
    }
}
