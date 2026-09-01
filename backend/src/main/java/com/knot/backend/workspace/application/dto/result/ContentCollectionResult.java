package com.knot.backend.workspace.application.dto.result;

import java.util.List;
import java.util.Objects;

public record ContentCollectionResult(List<CollectedPage> pages) {

    public ContentCollectionResult {
        pages = List.copyOf(
                Objects.requireNonNull(
                        pages,
                        "pages"
                )
        );
    }
}
