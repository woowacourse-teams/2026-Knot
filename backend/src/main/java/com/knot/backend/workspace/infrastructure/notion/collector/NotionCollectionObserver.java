package com.knot.backend.workspace.infrastructure.notion.collector;

interface NotionCollectionObserver {

    void recordSkippedBlock(
            String blockType,
            int count
    );

    void recordSkippedProperty(
            String propertyType,
            int count
    );
}
