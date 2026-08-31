package com.knot.backend.workspace.domain;

import java.util.List;

public interface NotionPageRepository {

    List<NotionPage> findAllByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId);
}
