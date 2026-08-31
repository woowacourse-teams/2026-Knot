package com.knot.backend.workspace.domain;

import java.util.Optional;

public interface NotionOAuthAuthorizationRepository {

    NotionOAuthAuthorization save(NotionOAuthAuthorization authorization);

    Optional<NotionOAuthAuthorization> findPendingByWorkspaceId(Long workspaceId);

    Optional<NotionOAuthAuthorization> findByStateHashForUpdate(String stateHash);

    boolean existsNewerAuthorization(
            Long workspaceId,
            Long authorizationId
    );
}
