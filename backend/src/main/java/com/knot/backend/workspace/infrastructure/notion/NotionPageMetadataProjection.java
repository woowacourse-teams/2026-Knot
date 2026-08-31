package com.knot.backend.workspace.infrastructure.notion;

interface NotionPageMetadataProjection {

    Long getId();

    Long getWorkspaceId();

    Long getParentPageId();

    String getTitle();

    int getPosition();

    String getNotionUrl();
}
