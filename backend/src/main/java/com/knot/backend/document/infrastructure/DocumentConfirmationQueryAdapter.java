package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentConfirmationQuery;
import com.knot.backend.document.application.dto.result.DocumentConfirmationItemResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationOverviewResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.domain.DocumentConfirmationCursor;
import com.knot.backend.document.domain.DocumentConfirmationState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

    @Override
    public List<DocumentConfirmationItemResult> findPage(
            long workspaceId,
            long documentId,
            int size,
            DocumentConfirmationCursor cursor
    ) {
        JdbcClient.StatementSpec statement = bindScope(
                jdbc.sql(TARGETS + """
                        , ordered_targets AS (
                            SELECT t.*, CASE
                                WHEN t.confirmed_at IS NOT NULL THEN :confirmedOrder
                                WHEN t.active_member THEN :pendingOrder
                                ELSE :excludedOrder
                            END AS state_order
                            FROM targets t
                        )
                        SELECT t.*, m.nickname, m.profile_image_url
                        FROM ordered_targets t JOIN members m ON m.id = t.member_id
                        """ + cursorCondition(cursor) + " ORDER BY t.state_order, t.member_id LIMIT :limit"),
                workspaceId,
                documentId
        ).param(
                "confirmedOrder",
                DocumentConfirmationState.CONFIRMED.getSortOrder()
        )
                .param(
                        "pendingOrder",
                        DocumentConfirmationState.PENDING.getSortOrder()
                )
                .param(
                        "excludedOrder",
                        DocumentConfirmationState.EXCLUDED.getSortOrder()
                )
                .param(
                        "limit",
                        size + 1
                );
        bindCursor(
                statement,
                cursor
        );
        return statement.query(this::mapItem)
                .list();
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

    private String cursorCondition(DocumentConfirmationCursor cursor) {
        if (cursor == null) {
            return "";
        }
        return " WHERE (t.state_order, t.member_id) > (:cursorOrder, :cursorMemberId)";
    }

    private void bindCursor(
            JdbcClient.StatementSpec statement,
            DocumentConfirmationCursor cursor
    ) {
        if (cursor == null) {
            return;
        }
        statement.param(
                "cursorOrder",
                cursor.getState()
                        .getSortOrder()
        )
                .param(
                        "cursorMemberId",
                        cursor.getTargetMemberId()
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

    private DocumentConfirmationItemResult mapItem(
            ResultSet row,
            int rowNumber
    ) throws SQLException {
        Instant confirmedAt = nullableInstant(row.getTimestamp("confirmed_at"));
        return new DocumentConfirmationItemResult(
                row.getLong("member_id"),
                row.getString("nickname"),
                row.getString("profile_image_url"),
                confirmedAt,
                DocumentConfirmationState.resolve(
                        row.getBoolean("active_member"),
                        confirmedAt
                )
        );
    }

    private Instant nullableInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }
}
