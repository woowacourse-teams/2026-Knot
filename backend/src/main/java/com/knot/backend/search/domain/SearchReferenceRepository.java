package com.knot.backend.search.domain;

import java.util.List;

public interface SearchReferenceRepository {

    List<SearchReference> findAllByMessageId(Long messageId);

    void replace(
            Long messageId,
            List<SearchChunk> references
    );
}
