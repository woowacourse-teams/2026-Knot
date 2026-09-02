package com.knot.backend.search.application;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.embedding")
public record EmbeddingProperties(
        String model,
        int dimensions
) {

    public void validate() {
        if (model == null || model.isBlank() || dimensions <= 0) {
            throw new SearchException(SearchErrorCode.SEARCH_CONFIGURATION_INVALID);
        }
    }
}
