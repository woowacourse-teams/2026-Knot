package com.knot.backend.workspace.domain;

import java.util.Optional;

public interface ContentSourceConnectionRepository {

    ContentSourceConnection save(ContentSourceConnection connection);

    Optional<ContentSourceConnection> findByIdAndWorkspaceId(
            Long connectionId,
            Long workspaceId
    );

    Optional<ContentSourceConnection> findByWorkspaceIdAndProvider(
            Long workspaceId,
            ContentSourceProvider provider
    );

    Optional<ContentSourceConnection> findByWorkspaceIdAndProviderForUpdate(
            Long workspaceId,
            ContentSourceProvider provider
    );
}
