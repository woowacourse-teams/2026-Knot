package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.Workspace;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface WorkspaceJpaRepository extends JpaRepository<Workspace, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Workspace> findWithLockById(Long workspaceId);
}
