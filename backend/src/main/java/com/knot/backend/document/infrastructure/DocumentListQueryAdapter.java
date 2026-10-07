package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentListQuery;
import com.knot.backend.document.application.dto.query.DocumentListParameters;
import com.knot.backend.document.application.dto.result.DocumentCardResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.application.dto.result.DocumentTopicResult;
import com.knot.backend.document.domain.DocumentCursor;
import com.knot.backend.document.domain.DocumentStatus;
import com.knot.backend.document.domain.MyConfirmationState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentListQueryAdapter implements DocumentListQuery {
    private final JdbcClient jdbc;
    private final DocumentListJpaRepository documents;

    @Override
    public List<DocumentTopicResult> findTopics(
            long workspaceId,
            long memberId,
            DocumentListParameters parameters
    ) {
        return documents.findTopics(
                workspaceId,
                memberId,
                parameters.recordingSessionId(),
                confirmationFilter(parameters.myConfirmation())
        )
                .stream()
                .map(
                        row -> new DocumentTopicResult(
                                row.topic(),
                                Math.toIntExact(row.documentCount())
                        )
                )
                .toList();
    }

    @Override
    public List<DocumentCardResult> findPage(
            long workspaceId,
            long memberId,
            DocumentListParameters parameters,
            DocumentCursor cursor
    ) {
        JdbcClient.StatementSpec statement = bind(
                jdbc.sql(
                        """
                                WITH page AS (
                                    SELECT d.id, d.recording_session_id, d.workspace_id, d.topic, d.title, d.summary, d.status, d.created_at
                                    FROM documents d
                                """
                                + filters(parameters) + cursorCondition(cursor)
                                + """
                                            ORDER BY d.created_at DESC, d.id DESC LIMIT :limit
                                        )
                                        SELECT d.*, rs.accumulated_recording_millis AS duration_millis,
                                            mine.document_id IS NOT NULL AS required_by_me, mine.confirmed_at AS confirmed_at_by_me,
                                            totals.confirmed_count, totals.pending_count, totals.excluded_count
                                        FROM page d
                                        JOIN recording_sessions rs ON rs.id = d.recording_session_id
                                        LEFT JOIN document_confirmations mine ON mine.document_id = d.id AND mine.member_id = :memberId
                                        CROSS JOIN LATERAL (
                                            SELECT count(*) FILTER (WHERE c.confirmed_at IS NOT NULL) AS confirmed_count,
                                                count(*) FILTER (WHERE c.confirmed_at IS NULL AND EXISTS (
                                                    SELECT 1 FROM workspace_members wm
                                                    WHERE wm.workspace_id = d.workspace_id AND wm.member_id = c.member_id AND wm.left_at IS NULL
                                                )) AS pending_count,
                                                count(*) FILTER (WHERE c.confirmed_at IS NULL AND NOT EXISTS (
                                                    SELECT 1 FROM workspace_members wm
                                                    WHERE wm.workspace_id = d.workspace_id AND wm.member_id = c.member_id AND wm.left_at IS NULL
                                                )) AS excluded_count
                                            FROM document_confirmations c WHERE c.document_id = d.id
                                        ) totals
                                        ORDER BY d.created_at DESC, d.id DESC
                                        """
                ),
                workspaceId,
                memberId,
                parameters
        ).param(
                "memberId",
                memberId
        )
                .param(
                        "limit",
                        parameters.size() + 1
                );
        if (cursor != null) {
            statement.param(
                    "cursorTime",
                    Timestamp.from(cursor.getCreatedAt())
            )
                    .param(
                            "cursorId",
                            cursor.getDocumentId()
                    );
        }
        return statement.query(this::mapRow)
                .list();
    }

    private String confirmationFilter(MyConfirmationState state) {
        if (state == null) {
            return "ALL";
        }
        return state.name();
    }

    private String filters(DocumentListParameters parameters) {
        return " WHERE d.workspace_id = :workspaceId" + recordingCondition(parameters.recordingSessionId())
                + confirmationCondition(parameters.myConfirmation());
    }

    private String cursorCondition(DocumentCursor cursor) {
        if (cursor == null) {
            return "";
        }
        return " AND (d.created_at, d.id) < (:cursorTime, :cursorId)";
    }

    private String recordingCondition(Long recordingSessionId) {
        if (recordingSessionId == null) {
            return "";
        }
        return " AND d.recording_session_id = :recordingId";
    }

    private String confirmationCondition(MyConfirmationState myConfirmation) {
        if (myConfirmation == null) {
            return "";
        }
        return switch (myConfirmation) {
            case PENDING -> """
                     AND EXISTS (SELECT 1 FROM document_confirmations c
                         WHERE c.document_id = d.id AND c.member_id = :memberId AND c.confirmed_at IS NULL)
                    """;
            case CONFIRMED -> """
                     AND EXISTS (SELECT 1 FROM document_confirmations c
                         WHERE c.document_id = d.id AND c.member_id = :memberId AND c.confirmed_at IS NOT NULL)
                    """;
            case NOT_REQUIRED -> """
                     AND NOT EXISTS (SELECT 1 FROM document_confirmations c
                         WHERE c.document_id = d.id AND c.member_id = :memberId)
                    """;
        };
    }

    private JdbcClient.StatementSpec bind(
            JdbcClient.StatementSpec statement,
            long workspaceId,
            long memberId,
            DocumentListParameters parameters
    ) {
        statement.param(
                "workspaceId",
                workspaceId
        );
        if (parameters.myConfirmation() != null) {
            statement.param(
                    "memberId",
                    memberId
            );
        }
        if (parameters.recordingSessionId() != null) {
            statement.param(
                    "recordingId",
                    parameters.recordingSessionId()
            );
        }
        return statement;
    }

    private DocumentCardResult mapRow(
            ResultSet row,
            int rowNumber
    ) throws SQLException {
        Timestamp confirmedAt = row.getTimestamp("confirmed_at_by_me");
        return new DocumentCardResult(
                row.getLong("id"),
                row.getLong("recording_session_id"),
                row.getString("topic"),
                row.getString("title"),
                row.getString("summary"),
                DocumentStatus.valueOf(row.getString("status")),
                row.getTimestamp("created_at")
                        .toInstant(),
                Math.toIntExact(row.getLong("duration_millis") / 1000),
                MyConfirmationState.resolve(
                        row.getBoolean("required_by_me"),
                        nullableInstant(confirmedAt)
                ),
                new DocumentConfirmationSummaryResult(
                        row.getInt("confirmed_count"),
                        row.getInt("pending_count"),
                        row.getInt("excluded_count")
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
