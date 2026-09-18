package com.knot.backend.search.infrastructure.persistence;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchChunkRepository;
import com.knot.backend.search.domain.SearchIndexedChunk;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcSearchChunkRepository implements SearchChunkRepository {
    private static final String CHUNK_COLUMNS = """
            chunk.workspace_id,
            chunk.imported_page_id,
            chunk.import_run_id,
            chunk.chunk_index,
            page.title,
            page.source_url,
            page.created_at,
            chunk.content
            """;

    private final JdbcClient jdbcClient;

    @Override
    public Optional<Long> findPublishedImportRunId(Long workspaceId) {
        return jdbcClient.sql("""
                SELECT publication.published_import_run_id
                FROM imported_page_publications publication
                JOIN content_import_runs import_run
                    ON import_run.id = publication.published_import_run_id
                    AND import_run.workspace_id = publication.workspace_id
                    AND import_run.status = 'COMPLETED'
                WHERE publication.workspace_id = :workspaceId
                    AND publication.published_import_status = 'COMPLETED'
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .list()
                .stream()
                .findFirst();
    }

    @Override
    public void replace(
            Long workspaceId,
            Long importRunId,
            List<SearchIndexedChunk> chunks
    ) {
        jdbcClient.sql("""
                DELETE FROM search_document_chunks
                WHERE workspace_id = :workspaceId
                    AND import_run_id = :importRunId
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "importRunId",
                        importRunId
                )
                .update();
        for (SearchIndexedChunk chunk : chunks) {
            jdbcClient.sql("""
                    INSERT INTO search_document_chunks (
                        workspace_id,
                        import_run_id,
                        imported_page_id,
                        chunk_index,
                        content,
                        embedding
                    ) VALUES (
                        :workspaceId,
                        :importRunId,
                        :importedPageId,
                        :chunkIndex,
                        :content,
                        CAST(:embedding AS vector)
                    )
                    """)
                    .param(
                            "workspaceId",
                            workspaceId
                    )
                    .param(
                            "importRunId",
                            importRunId
                    )
                    .param(
                            "importedPageId",
                            chunk.importedPageId()
                    )
                    .param(
                            "chunkIndex",
                            chunk.chunkIndex()
                    )
                    .param(
                            "content",
                            chunk.content()
                    )
                    .param(
                            "embedding",
                            vectorLiteral(chunk.embedding())
                    )
                    .update();
        }
    }

    @Override
    public List<SearchChunk> findByVector(
            Long workspaceId,
            Long importRunId,
            double[] embedding,
            int limit
    ) {
        return jdbcClient.sql("""
                SELECT
                    %s,
                    1 - (chunk.embedding <=> CAST(:embedding AS vector)) AS score
                FROM search_document_chunks chunk
                JOIN imported_pages page
                    ON page.id = chunk.imported_page_id
                    AND page.workspace_id = chunk.workspace_id
                    AND page.import_run_id = chunk.import_run_id
                JOIN imported_page_publications publication
                    ON publication.workspace_id = chunk.workspace_id
                    AND publication.published_import_run_id = chunk.import_run_id
                    AND publication.published_import_status = 'COMPLETED'
                JOIN content_import_runs import_run
                    ON import_run.id = publication.published_import_run_id
                    AND import_run.workspace_id = publication.workspace_id
                    AND import_run.status = 'COMPLETED'
                WHERE chunk.workspace_id = :workspaceId
                    AND chunk.import_run_id = :importRunId
                ORDER BY chunk.embedding <=> CAST(:embedding AS vector), chunk.id
                LIMIT :limit
                """.formatted(CHUNK_COLUMNS))
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "importRunId",
                        importRunId
                )
                .param(
                        "embedding",
                        vectorLiteral(embedding)
                )
                .param(
                        "limit",
                        limit
                )
                .query(this::mapChunk)
                .list();
    }

    @Override
    public List<SearchChunk> findByKeywords(
            Long workspaceId,
            Long importRunId,
            List<String> terms,
            int limit
    ) {
        if (terms.isEmpty()) {
            return List.of();
        }
        String condition = IntStream.range(
                0,
                terms.size()
        )
                .mapToObj(this::termCondition)
                .reduce(
                        (
                                left,
                                right
                        ) -> left + " OR " + right
                )
                .orElseThrow();
        String scoreExpression = IntStream.range(
                0,
                terms.size()
        )
                .mapToObj(this::termScore)
                .reduce(
                        (
                                left,
                                right
                        ) -> left + " + " + right
                )
                .orElseThrow();
        String sql = """
                SELECT
                    %s,
                    (%s) / CAST(:termCount * 2 AS DOUBLE PRECISION) AS score
                FROM search_document_chunks chunk
                JOIN imported_pages page
                    ON page.id = chunk.imported_page_id
                    AND page.workspace_id = chunk.workspace_id
                    AND page.import_run_id = chunk.import_run_id
                JOIN imported_page_publications publication
                    ON publication.workspace_id = chunk.workspace_id
                    AND publication.published_import_run_id = chunk.import_run_id
                    AND publication.published_import_status = 'COMPLETED'
                JOIN content_import_runs import_run
                    ON import_run.id = publication.published_import_run_id
                    AND import_run.workspace_id = publication.workspace_id
                    AND import_run.status = 'COMPLETED'
                WHERE chunk.workspace_id = :workspaceId
                    AND chunk.import_run_id = :importRunId
                    AND (%s)
                ORDER BY score DESC, chunk.id
                LIMIT :limit
                """.formatted(
                CHUNK_COLUMNS,
                scoreExpression,
                condition
        );
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "importRunId",
                        importRunId
                )
                .param(
                        "termCount",
                        terms.size()
                )
                .param(
                        "limit",
                        limit
                );
        for (int index = 0; index < terms.size(); index++) {
            statement = statement.param(
                    "term" + index,
                    terms.get(index)
            );
        }
        return statement.query(this::mapChunk)
                .list();
    }

    private String termCondition(int index) {
        return "POSITION(LOWER(:term" + index + ") IN LOWER(page.title)) > 0" + " OR POSITION(LOWER(:term" + index
                + ") IN LOWER(chunk.content)) > 0";
    }

    private String termScore(int index) {
        return "CASE WHEN POSITION(LOWER(:term" + index + ") IN LOWER(page.title)) > 0 THEN 1.0 ELSE 0.0 END"
                + " + CASE WHEN POSITION(LOWER(:term" + index + ") IN LOWER(chunk.content)) > 0"
                + " THEN 1.0 ELSE 0.0 END";
    }

    private SearchChunk mapChunk(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return SearchChunk.retrieved(
                resultSet.getLong("workspace_id"),
                resultSet.getLong("imported_page_id"),
                resultSet.getLong("import_run_id"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("title"),
                resultSet.getString("source_url"),
                resultSet.getTimestamp("created_at")
                        .toInstant(),
                resultSet.getString("content"),
                resultSet.getDouble("score")
        );
    }

    private String vectorLiteral(double[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(embedding[index]);
        }
        return literal.append(']')
                .toString();
    }
}
