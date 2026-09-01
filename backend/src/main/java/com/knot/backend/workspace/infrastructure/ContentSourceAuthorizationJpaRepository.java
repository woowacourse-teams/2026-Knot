package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.ContentSourceAuthorization;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ContentSourceAuthorizationJpaRepository extends JpaRepository<ContentSourceAuthorization, Long> {

    Optional<ContentSourceAuthorization> findByWorkspaceIdAndProviderAndConsumedAtIsNullAndInvalidatedAtIsNull(
            Long workspaceId,
            ContentSourceProvider provider
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select authorization
            from ContentSourceAuthorization authorization
            where authorization.provider = :provider
              and authorization.stateHash = :stateHash
            """)
    Optional<ContentSourceAuthorization> findByProviderAndStateHashForUpdate(
            ContentSourceProvider provider,
            String stateHash
    );

    boolean existsByWorkspaceIdAndProviderAndIdGreaterThan(
            Long workspaceId,
            ContentSourceProvider provider,
            Long authorizationId
    );
}
