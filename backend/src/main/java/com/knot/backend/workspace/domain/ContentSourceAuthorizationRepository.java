package com.knot.backend.workspace.domain;

import java.util.Optional;

public interface ContentSourceAuthorizationRepository {

    ContentSourceAuthorization save(ContentSourceAuthorization authorization);

    Optional<ContentSourceAuthorization> findPendingByWorkspaceIdAndProvider(
            Long workspaceId,
            ContentSourceProvider provider
    );

    Optional<ContentSourceAuthorization> findByProviderAndStateHashForUpdate(
            ContentSourceProvider provider,
            String stateHash
    );

    boolean existsNewerAuthorization(
            Long workspaceId,
            ContentSourceProvider provider,
            Long authorizationId
    );
}
