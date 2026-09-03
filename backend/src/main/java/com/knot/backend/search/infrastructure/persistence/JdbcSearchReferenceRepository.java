package com.knot.backend.search.infrastructure.persistence;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.search.domain.SearchReference;
import com.knot.backend.search.domain.SearchReferenceRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcSearchReferenceRepository implements SearchReferenceRepository {
    private final JdbcClient jdbcClient;

    @Override
    public List<SearchReference> findAllByMessageId(Long messageId) {
        return jdbcClient.sql("""
                SELECT
                    reference.id AS reference_id,
                    reference.message_id,
                    reference.reference_rank,
                    reference.relevance_score,
                    connection.provider AS source,
                    page.external_page_id,
                    page.title,
                    page.source_url,
                    page.created_at,
                    page.updated_at
                FROM search_references reference
                JOIN chat_messages message
                    ON message.id = reference.message_id
                JOIN chat_sessions session
                    ON session.id = message.session_id
                    AND session.workspace_id = reference.workspace_id
                JOIN imported_pages page
                    ON page.id = reference.imported_page_id
                    AND page.workspace_id = reference.workspace_id
                    AND page.import_run_id = reference.import_run_id
                JOIN content_import_runs import_run
                    ON import_run.id = reference.import_run_id
                    AND import_run.workspace_id = reference.workspace_id
                JOIN content_source_connections connection
                    ON connection.id = import_run.content_source_connection_id
                    AND connection.workspace_id = import_run.workspace_id
                WHERE reference.message_id = :messageId
                ORDER BY reference.reference_rank ASC
                """)
                .param(
                        "messageId",
                        messageId
                )
                .query(this::mapSearchReference)
                .list();
    }

    @Override
    public void replace(
            Long messageId,
            List<SearchChunk> references
    ) {
        jdbcClient.sql("""
                DELETE FROM search_references
                WHERE message_id = :messageId
                """)
                .param(
                        "messageId",
                        messageId
                )
                .update();
        for (int index = 0; index < references.size(); index++) {
            SearchChunk reference = references.get(index);
            int inserted = jdbcClient.sql("""
                    INSERT INTO search_references (
                        message_id,
                        workspace_id,
                        import_run_id,
                        imported_page_id,
                        reference_rank,
                        relevance_score
                    )
                    SELECT
                        message.id,
                        session.workspace_id,
                        :importRunId,
                        page.id,
                        :referenceRank,
                        :relevanceScore
                    FROM chat_messages message
                    JOIN chat_sessions session
                        ON session.id = message.session_id
                    JOIN imported_pages page
                        ON page.id = :importedPageId
                        AND page.workspace_id = session.workspace_id
                        AND page.import_run_id = :importRunId
                    WHERE message.id = :messageId
                    """)
                    .param(
                            "messageId",
                            messageId
                    )
                    .param(
                            "importRunId",
                            reference.importRunId()
                    )
                    .param(
                            "importedPageId",
                            reference.importedPageId()
                    )
                    .param(
                            "referenceRank",
                            index + 1
                    )
                    .param(
                            "relevanceScore",
                            Math.max(
                                    0,
                                    Math.min(
                                            reference.score(),
                                            1
                                    )
                            )
                    )
                    .update();
            if (inserted != 1) {
                throw new SearchException(SearchErrorCode.SEARCH_REFERENCE_FAILED);
            }
        }
    }

    private SearchReference mapSearchReference(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new SearchReference(
                resultSet.getLong("reference_id"),
                resultSet.getLong("message_id"),
                resultSet.getInt("reference_rank"),
                resultSet.getDouble("relevance_score"),
                ContentSourceProvider.valueOf(resultSet.getString("source")),
                new SearchReference.ContentPageReference(
                        resultSet.getString("external_page_id"),
                        resultSet.getString("title"),
                        resultSet.getString("source_url"),
                        resultSet.getTimestamp("created_at")
                                .toInstant(),
                        resultSet.getTimestamp("updated_at")
                                .toInstant()
                )
        );
    }
}
