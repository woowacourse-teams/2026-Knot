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
    Optional<Workspace> findWithLockByIdAndDeletedAtIsNull(Long workspaceId);

    Optional<Workspace> findByIdAndDeletedAtIsNull(Long workspaceId);

    @Query(value = """
            SELECT w.*
            FROM workspace_members wm
            JOIN workspaces w ON w.id = wm.workspace_id
            WHERE wm.member_id = :memberId
              AND wm.left_at IS NULL
              AND w.deleted_at IS NULL
            ORDER BY wm.joined_at DESC, w.id DESC
            """, nativeQuery = true)
    List<Workspace> findAllByMemberId(Long memberId);

    // 이름·내 역할·활성 멤버 수가 승계·탈퇴 전후의 서로 다른 시점으로 섞이지 않도록 한 문장으로 읽는다
    @Query(value = """
            SELECT w.name AS name,
                (
                    SELECT wm.role FROM workspace_members wm
                    WHERE wm.workspace_id = w.id AND wm.member_id = :memberId AND wm.left_at IS NULL
                ) AS my_role,
                (
                    SELECT count(*) FROM workspace_members wm
                    WHERE wm.workspace_id = w.id AND wm.left_at IS NULL
                ) AS active_member_count
            FROM workspaces w
            WHERE w.id = :workspaceId
              AND w.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<WorkspaceDetailRow> findActiveDetail(
            long workspaceId,
            long memberId
    );
}
