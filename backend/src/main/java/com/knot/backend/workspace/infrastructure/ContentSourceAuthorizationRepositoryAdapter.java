package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.ContentSourceAuthorization;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ContentSourceAuthorizationRepositoryAdapter implements ContentSourceAuthorizationRepository {
    private final ContentSourceAuthorizationJpaRepository authorizationJpaRepository;

    public ContentSourceAuthorizationRepositoryAdapter(
            ContentSourceAuthorizationJpaRepository authorizationJpaRepository
    ) {
        this.authorizationJpaRepository = authorizationJpaRepository;
    }

    @Override
    public ContentSourceAuthorization save(ContentSourceAuthorization authorization) {
        return authorizationJpaRepository.saveAndFlush(authorization);
    }

    @Override
    public Optional<ContentSourceAuthorization> findPendingByWorkspaceIdAndProvider(
            Long workspaceId,
            ContentSourceProvider provider
    ) {
        return authorizationJpaRepository.findByWorkspaceIdAndProviderAndConsumedAtIsNullAndInvalidatedAtIsNull(
                workspaceId,
                provider
        );
    }

    @Override
    public Optional<ContentSourceAuthorization> findByProviderAndStateHashForUpdate(
            ContentSourceProvider provider,
            String stateHash
    ) {
        return authorizationJpaRepository.findByProviderAndStateHashForUpdate(
                provider,
                stateHash
        );
    }

    @Override
    public boolean existsNewerAuthorization(
            Long workspaceId,
            ContentSourceProvider provider,
            Long authorizationId
    ) {
        return authorizationJpaRepository.existsByWorkspaceIdAndProviderAndIdGreaterThan(
                workspaceId,
                provider,
                authorizationId
        );
    }
}
