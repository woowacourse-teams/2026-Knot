package com.knot.backend.workspace.application.dto.result;

import java.util.List;
import java.util.Objects;

public record NotionCollectionResult(List<CollectedNotionPage> pages) {

    public NotionCollectionResult {
        pages = List.copyOf(
                Objects.requireNonNull(
                        pages,
                        "pages"
                )
        );
    }
}
