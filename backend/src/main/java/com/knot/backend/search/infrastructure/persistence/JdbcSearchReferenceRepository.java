package com.knot.backend.search.infrastructure.persistence;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.search.domain.SearchReferenceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcSearchReferenceRepository implements SearchReferenceRepository {
    private final JdbcClient jdbcClient;

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
}
