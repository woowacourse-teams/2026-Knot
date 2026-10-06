package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentConfirmationQuery;
import com.knot.backend.document.application.dto.result.DocumentConfirmationOverviewResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentConfirmationQueryAdapter implements DocumentConfirmationQuery {
    private static final String TARGETS = """
            WITH targets AS (
                SELECT c.member_id, c.confirmed_at, EXISTS (
                    SELECT 1 FROM workspace_members wm
                    WHERE wm.workspace_id = d.workspace_id AND wm.member_id = c.member_id AND wm.left_at IS NULL
                ) AS active_member
                FROM document_confirmations c
                JOIN documents d ON d.id = c.document_id
                WHERE d.workspace_id = :workspaceId AND d.id = :documentId
            )
            """;
    private final JdbcClient jdbc;

    @Override
    public Optional<DocumentConfirmationOverviewResult> findSummary(
            long workspaceId,
            long documentId,
            long memberId
    ) {
        return bindScope(
                jdbc.sql(TARGETS + """
                        SELECT d.id, totals.*, EXISTS (
                            SELECT 1 FROM targets t WHERE t.member_id = :memberId AND t.confirmed_at IS NOT NULL
                        ) AS confirmed_by_me
                        FROM documents d
                        CROSS JOIN LATERAL (
                            SELECT count(*) FILTER (WHERE confirmed_at IS NOT NULL) AS confirmed_count,
                                count(*) FILTER (WHERE confirmed_at IS NULL AND active_member) AS pending_count,
                                count(*) FILTER (WHERE confirmed_at IS NULL AND NOT active_member) AS excluded_count
                            FROM targets
                        ) totals
                        WHERE d.workspace_id = :workspaceId AND d.id = :documentId
                        """),
                workspaceId,
                documentId
        ).param(
                "memberId",
                memberId
        )
                .query(this::mapSummary)
                .optional();
    }

    private JdbcClient.StatementSpec bindScope(
            JdbcClient.StatementSpec statement,
            long workspaceId,
            long documentId
    ) {
        return statement.param(
                "workspaceId",
                workspaceId
        )
                .param(
                        "documentId",
                        documentId
                );
    }

    private DocumentConfirmationOverviewResult mapSummary(
            ResultSet row,
            int rowNumber
    ) throws SQLException {
        return new DocumentConfirmationOverviewResult(
                row.getLong("id"),
                new DocumentConfirmationSummaryResult(
                        row.getInt("confirmed_count"),
                        row.getInt("pending_count"),
                        row.getInt("excluded_count")
                ),
                row.getBoolean("confirmed_by_me")
        );
    }

}
