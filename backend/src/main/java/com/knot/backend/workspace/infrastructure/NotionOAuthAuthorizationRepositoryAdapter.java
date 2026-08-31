package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.NotionOAuthAuthorization;
import com.knot.backend.workspace.domain.NotionOAuthAuthorizationRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class NotionOAuthAuthorizationRepositoryAdapter implements NotionOAuthAuthorizationRepository {
    private final NotionOAuthAuthorizationJpaRepository notionOAuthAuthorizationJpaRepository;

    public NotionOAuthAuthorizationRepositoryAdapter(
            NotionOAuthAuthorizationJpaRepository notionOAuthAuthorizationJpaRepository
    ) {
        this.notionOAuthAuthorizationJpaRepository = notionOAuthAuthorizationJpaRepository;
    }

    @Override
    public NotionOAuthAuthorization save(NotionOAuthAuthorization authorization) {
        return notionOAuthAuthorizationJpaRepository.saveAndFlush(authorization);
    }

    @Override
    public Optional<NotionOAuthAuthorization> findPendingByWorkspaceId(Long workspaceId) {
        return notionOAuthAuthorizationJpaRepository
                .findByWorkspaceIdAndConsumedAtIsNullAndInvalidatedAtIsNull(workspaceId);
    }

    @Override
    public Optional<NotionOAuthAuthorization> findByStateHashForUpdate(String stateHash) {
        return notionOAuthAuthorizationJpaRepository.findByStateHashForUpdate(stateHash);
    }

    @Override
    public boolean existsNewerAuthorization(
            Long workspaceId,
            Long authorizationId
    ) {
        return notionOAuthAuthorizationJpaRepository.existsByWorkspaceIdAndIdGreaterThan(
                workspaceId,
                authorizationId
        );
    }
}
