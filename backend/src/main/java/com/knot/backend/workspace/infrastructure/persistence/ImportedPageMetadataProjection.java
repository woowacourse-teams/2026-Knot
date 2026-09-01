package com.knot.backend.workspace.infrastructure.persistence;

interface ImportedPageMetadataProjection {

    Long getId();

    Long getWorkspaceId();

    Long getParentId();

    boolean getHasParentReference();

    String getTitle();

    int getPosition();

    String getSourceUrl();
}
