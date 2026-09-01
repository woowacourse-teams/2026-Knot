package com.knot.backend.search.infrastructure.persistence;

import com.knot.backend.search.domain.SearchChunk;
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
            jdbcClient.sql("""
                    INSERT INTO search_references (
                        message_id,
                        workspace_id,
                        import_run_id,
                        imported_page_id,
                        reference_rank,
                        relevance_score
                    ) VALUES (
                        :messageId,
                        :workspaceId,
                        :importRunId,
                        :importedPageId,
                        :referenceRank,
                        :relevanceScore
                    )
                    """)
                    .param(
                            "messageId",
                            messageId
                    )
                    .param(
                            "workspaceId",
                            reference.workspaceId()
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
        }
    }
}
