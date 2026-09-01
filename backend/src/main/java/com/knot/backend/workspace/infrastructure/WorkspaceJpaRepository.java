package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.Workspace;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface WorkspaceJpaRepository extends JpaRepository<Workspace, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Workspace> findWithLockById(Long workspaceId);

    @Query(value = """
            SELECT w.*
            FROM workspace_members wm
            JOIN workspaces w ON w.id = wm.workspace_id
            WHERE wm.member_id = :memberId
            ORDER BY wm.joined_at DESC, w.id DESC
            """, nativeQuery = true)
    List<Workspace> findAllByMemberId(Long memberId);
}
