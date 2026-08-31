package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.NotionConnection;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface NotionConnectionJpaRepository extends JpaRepository<NotionConnection, Long> {

    Optional<NotionConnection> findByWorkspaceId(Long workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select connection from NotionConnection connection where connection.workspaceId = :workspaceId")
    Optional<NotionConnection> findByWorkspaceIdForUpdate(Long workspaceId);
}
