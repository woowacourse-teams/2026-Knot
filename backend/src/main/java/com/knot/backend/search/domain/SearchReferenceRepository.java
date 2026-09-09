package com.knot.backend.search.domain;

import java.util.List;

public interface SearchReferenceRepository {

    List<SearchReference> findAllByMessageId(Long messageId);

    default void replace(
            Long messageId,
            List<SearchChunk> references
    ) {
        replaceCandidates(
                messageId,
                references.stream()
                        .map(SearchReferenceCandidate::from)
                        .toList()
        );
    }

    /**
     * 메시지의 근거를 통째로 바꾼다. 각 근거는 메시지가 속한 Workspace의 페이지여야 하며, 아니면
     * {@link SearchErrorCode#SEARCH_REFERENCE_FAILED}다. 배열 순서가 rank(1부터)이고 점수는 0~1로 잘린다.
     */
    void replaceCandidates(
            Long messageId,
            List<SearchReferenceCandidate> references
    );
}
