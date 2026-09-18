package com.knot.backend.search.application;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.search")
public record SearchProperties(
        int chunkSize,
        int chunkOverlap,
        int candidateLimit,
        int topK,
        int maxContextCharacters,
        int embeddingBatchSize,
        double minimumRelevanceScore
) {

    public void validate() {
        if (chunkSize <= 0 || chunkOverlap < 0 || chunkOverlap >= chunkSize || candidateLimit <= 0 || topK <= 0
                || topK > candidateLimit || maxContextCharacters <= 0 || embeddingBatchSize <= 0
                || !Double.isFinite(minimumRelevanceScore) || minimumRelevanceScore < 0 || minimumRelevanceScore > 1) {
            throw new SearchException(SearchErrorCode.SEARCH_CONFIGURATION_INVALID);
        }
    }
}
