package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentDetailQuery;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.application.dto.result.DocumentDetailSnapshot;
import com.knot.backend.document.domain.DocumentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentDetailQueryAdapter implements DocumentDetailQuery {
    private final JdbcClient jdbc;

    @Override
    public Optional<DocumentDetailSnapshot> find(
            long workspaceId,
            long documentId,
            long memberId
    ) {
        return jdbc.sql("""
                SELECT d.*, rs.accumulated_recording_millis AS recording_duration_millis,
                    mine.document_id IS NOT NULL AS required_by_me, mine.confirmed_at AS confirmed_at_by_me,
                    totals.confirmed_count, totals.pending_count, totals.excluded_count
                FROM documents d
                JOIN recording_sessions rs ON rs.id = d.recording_session_id
                LEFT JOIN document_confirmations mine ON mine.document_id = d.id AND mine.member_id = :memberId
                CROSS JOIN LATERAL (
                    SELECT count(*) FILTER (WHERE c.confirmed_at IS NOT NULL) AS confirmed_count,
                        count(*) FILTER (WHERE c.confirmed_at IS NULL AND EXISTS (
                            SELECT 1 FROM workspace_members wm
                            WHERE wm.workspace_id = d.workspace_id AND wm.member_id = c.member_id
                                AND wm.left_at IS NULL
                        )) AS pending_count,
                        count(*) FILTER (WHERE c.confirmed_at IS NULL AND NOT EXISTS (
                            SELECT 1 FROM workspace_members wm
                            WHERE wm.workspace_id = d.workspace_id AND wm.member_id = c.member_id
                                AND wm.left_at IS NULL
                        )) AS excluded_count
                    FROM document_confirmations c WHERE c.document_id = d.id
                ) totals
                WHERE d.workspace_id = :workspaceId AND d.id = :documentId
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "documentId",
                        documentId
                )
                .param(
                        "memberId",
                        memberId
                )
                .query(this::mapRow)
                .optional();
    }

    private DocumentDetailSnapshot mapRow(
            ResultSet row,
            int rowNumber
    ) throws SQLException {
        return new DocumentDetailSnapshot(
                row.getLong("id"),
                row.getLong("recording_session_id"),
                row.getString("topic"),
                row.getString("title"),
                row.getString("summary"),
                row.getString("content"),
                DocumentStatus.valueOf(row.getString("status")),
                row.getTimestamp("created_at")
                        .toInstant(),
                nullableInstant(
                        row,
                        "archived_at"
                ),
                row.getLong("recording_duration_millis"),
                row.getLong("source_transcript_id"),
                row.getBoolean("required_by_me"),
                nullableInstant(
                        row,
                        "confirmed_at_by_me"
                ),
                new DocumentConfirmationSummaryResult(
                        row.getInt("confirmed_count"),
                        row.getInt("pending_count"),
                        row.getInt("excluded_count")
                )
        );
    }

    private Instant nullableInstant(
            ResultSet row,
            String column
    ) throws SQLException {
        Timestamp timestamp = row.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
