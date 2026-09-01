package com.knot.backend.search.domain;

import java.util.List;

public interface SearchReferenceRepository {

    void replace(
            Long messageId,
            List<SearchChunk> references
    );
}
