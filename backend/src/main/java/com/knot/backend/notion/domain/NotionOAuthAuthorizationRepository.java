package com.knot.backend.notion.domain;

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
