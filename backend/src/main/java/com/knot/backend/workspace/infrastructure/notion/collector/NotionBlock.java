package com.knot.backend.workspace.infrastructure.notion.collector;

import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

record NotionBlock(
        JsonNode value,
        List<NotionBlock> children
) {

    NotionBlock {
        Objects.requireNonNull(
                value,
                "value"
        );
        children = List.copyOf(
                Objects.requireNonNull(
                        children,
                        "children"
                )
        );
    }
}
