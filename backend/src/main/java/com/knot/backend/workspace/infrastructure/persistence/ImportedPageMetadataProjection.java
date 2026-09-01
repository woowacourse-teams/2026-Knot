package com.knot.backend.workspace.infrastructure.persistence;

interface ImportedPageMetadataProjection {

    Long getId();

    Long getWorkspaceId();

    Long getParentId();

    String getTitle();

    int getPosition();

    String getSourceUrl();
}
