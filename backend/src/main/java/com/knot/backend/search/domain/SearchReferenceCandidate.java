package com.knot.backend.search.domain;

/**
 * 저장할 근거 한 건의 식별자와 점수. 클라이언트(CLI 에이전트)가 검색 결과에서 골라 보낸 값이며, Workspace 소속 여부는
 * {@link SearchReferenceRepository}가 INSERT…SELECT의 JOIN으로만 검증한다(데스크톱 로드맵 Q24). 순서가 rank다.
 */
public record SearchReferenceCandidate(
        Long importRunId,
        Long importedPageId,
        int chunkIndex,
        double score
) {

    public static SearchReferenceCandidate from(SearchChunk chunk) {
        return new SearchReferenceCandidate(
                chunk.importRunId(),
                chunk.importedPageId(),
                chunk.chunkIndex(),
                chunk.score()
        );
    }
}
