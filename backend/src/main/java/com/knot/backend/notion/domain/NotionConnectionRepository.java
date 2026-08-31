package com.knot.backend.notion.domain;

import java.util.Optional;

public interface NotionConnectionRepository {

    NotionConnection save(NotionConnection connection);

    Optional<NotionConnection> findByWorkspaceId(Long workspaceId);

    Optional<NotionConnection> findByWorkspaceIdForUpdate(Long workspaceId);
}
