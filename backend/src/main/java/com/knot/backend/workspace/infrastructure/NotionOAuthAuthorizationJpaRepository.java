package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.NotionOAuthAuthorization;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface NotionOAuthAuthorizationJpaRepository extends JpaRepository<NotionOAuthAuthorization, Long> {

    Optional<NotionOAuthAuthorization> findByWorkspaceIdAndConsumedAtIsNullAndInvalidatedAtIsNull(Long workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select authorization
            from NotionOAuthAuthorization authorization
            where authorization.stateHash = :stateHash
            """)
    Optional<NotionOAuthAuthorization> findByStateHashForUpdate(String stateHash);

    boolean existsByWorkspaceIdAndIdGreaterThan(
            Long workspaceId,
            Long authorizationId
    );
}
