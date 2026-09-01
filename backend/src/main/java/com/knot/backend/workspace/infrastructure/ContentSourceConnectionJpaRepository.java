package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ContentSourceConnectionJpaRepository extends JpaRepository<ContentSourceConnection, Long> {

    Optional<ContentSourceConnection> findByIdAndWorkspaceId(
            Long connectionId,
            Long workspaceId
    );

    Optional<ContentSourceConnection> findByWorkspaceIdAndProvider(
            Long workspaceId,
            ContentSourceProvider provider
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select connection
            from ContentSourceConnection connection
            where connection.workspaceId = :workspaceId
              and connection.provider = :provider
            """)
    Optional<ContentSourceConnection> findByWorkspaceIdAndProviderForUpdate(
            Long workspaceId,
            ContentSourceProvider provider
    );
}
