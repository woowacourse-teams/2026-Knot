package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface WorkspaceMemberJpaRepository extends JpaRepository<WorkspaceMember, Long> {

    @Query(value = """
            SELECT wm.* FROM workspace_members wm
            JOIN workspaces w ON w.id = wm.workspace_id
            WHERE wm.member_id = :memberId
              AND wm.left_at IS NULL AND w.deleted_at IS NULL
            ORDER BY wm.id
            FOR UPDATE OF wm
            """, nativeQuery = true)
    List<WorkspaceMember> findAllActiveByMemberIdForUpdate(Long memberId);

    @Query(value = """
            SELECT wm.* FROM workspace_members wm
            WHERE wm.workspace_id = :workspaceId
              AND wm.member_id IN (:memberIds)
              AND wm.left_at IS NULL
            ORDER BY wm.id
            FOR UPDATE OF wm
            """, nativeQuery = true)
    List<WorkspaceMember> findAllActiveByWorkspaceIdAndMemberIdsForUpdate(
            Long workspaceId,
            List<Long> memberIds
    );

    @Query(value = """
            SELECT wm.* FROM workspace_members wm
            WHERE wm.workspace_id = :workspaceId
              AND wm.left_at IS NULL
            ORDER BY wm.id
            FOR UPDATE OF wm
            """, nativeQuery = true)
    List<WorkspaceMember> findAllActiveByWorkspaceIdForUpdate(Long workspaceId);

    @Query(value = """
            SELECT wm.*
            FROM workspace_members wm
            JOIN workspaces w ON w.id = wm.workspace_id
            WHERE wm.member_id = :memberId
              AND wm.last_viewed = TRUE
              AND wm.left_at IS NULL
              AND w.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<WorkspaceMember> findActiveLastViewedByMemberId(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkspaceMember> findFirstByWorkspaceIdAndMemberIdOrderByIdDesc(
            Long workspaceId,
            Long memberId
    );

    @Query(value = """
            SELECT count(*) FROM workspace_members wm
            JOIN workspaces w ON w.id = wm.workspace_id
            WHERE wm.workspace_id = :workspaceId
              AND wm.left_at IS NULL AND w.deleted_at IS NULL
            """, nativeQuery = true)
    long countActiveByWorkspaceId(Long workspaceId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM workspace_members wm
                JOIN workspaces w ON w.id = wm.workspace_id
                WHERE wm.workspace_id = :workspaceId AND wm.member_id = :memberId
                  AND wm.left_at IS NULL AND w.deleted_at IS NULL
            )
            """, nativeQuery = true)
    boolean existsActiveByWorkspaceIdAndMemberId(
            Long workspaceId,
            Long memberId
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM workspace_members wm
                JOIN workspaces w ON w.id = wm.workspace_id
                WHERE wm.workspace_id = :workspaceId AND wm.member_id = :memberId
                  AND wm.role = :#{#role.name()}
                  AND wm.left_at IS NULL AND w.deleted_at IS NULL
            )
            """, nativeQuery = true)
    boolean existsActiveByWorkspaceIdAndMemberIdAndRole(
            Long workspaceId,
            Long memberId,
            WorkspaceMemberRole role
    );

    @Query(value = """
            SELECT wm.member_id, m.nickname
            FROM workspace_members wm
            JOIN members m ON m.id = wm.member_id
            JOIN workspaces w ON w.id = wm.workspace_id
            WHERE wm.workspace_id = :workspaceId
              AND wm.member_id <> :ownerMemberId
              AND wm.role = 'MEMBER'
              AND wm.left_at IS NULL
              AND w.deleted_at IS NULL
            ORDER BY wm.member_id ASC
            """, nativeQuery = true)
    List<WorkspaceOwnershipTransferCandidateRow> findActiveOwnershipTransferCandidates(
            Long workspaceId,
            long ownerMemberId
    );
}
