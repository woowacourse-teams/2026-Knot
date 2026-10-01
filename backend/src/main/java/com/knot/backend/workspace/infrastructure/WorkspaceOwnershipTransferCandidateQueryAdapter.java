package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.application.WorkspaceOwnershipTransferCandidateQuery;
import com.knot.backend.workspace.application.dto.result.WorkspaceOwnershipTransferCandidateResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WorkspaceOwnershipTransferCandidateQueryAdapter implements WorkspaceOwnershipTransferCandidateQuery {
    private final JdbcClient jdbcClient;

    @Override
    public List<WorkspaceOwnershipTransferCandidateResult> findAll(
            Long workspaceId,
            long ownerMemberId
    ) {
        return jdbcClient.sql("""
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
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "ownerMemberId",
                        ownerMemberId
                )
                .query(
                        (
                                row,
                                rowNumber
                        ) -> new WorkspaceOwnershipTransferCandidateResult(
                                row.getLong("member_id"),
                                row.getString("nickname")
                        )
                )
                .list();
    }
}
