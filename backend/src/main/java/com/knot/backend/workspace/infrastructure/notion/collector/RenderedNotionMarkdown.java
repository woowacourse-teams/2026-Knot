package com.knot.backend.workspace.infrastructure.notion.collector;

import java.util.Map;

record RenderedNotionMarkdown(
        String markdown,
        Map<String, Integer> skippedBlockCounts
) {

    RenderedNotionMarkdown {
        skippedBlockCounts = Map.copyOf(skippedBlockCounts);
    }
}
