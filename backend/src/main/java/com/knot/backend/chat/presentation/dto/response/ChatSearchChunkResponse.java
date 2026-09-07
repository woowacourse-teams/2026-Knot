package com.knot.backend.chat.presentation.dto.response;

import com.knot.backend.search.domain.SearchChunk;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "검색으로 고른 근거 청크. 데스크톱이 이 순서대로 [근거 문서 n] 블록을 만든다")
public record ChatSearchChunkResponse(
        @Schema(description = "청크가 속한 import run ID", example = "301") long importRunId,
        @Schema(description = "청크가 속한 문서(imported page) ID", example = "201") long importedPageId,
        @Schema(description = "문서 안의 청크 순번(0부터)", example = "2") int chunkIndex,
        @Schema(description = "문서 제목", example = "DB 기술 선정 회의록") String title,
        @Schema(description = "원본 문서 링크", example = "https://www.notion.so/notion-page-1") String sourceUrl,
        @Schema(description = "청크 본문. 프롬프트 예산에 맞춰 서버가 자른 값") String content,
        @Schema(description = "융합 관련도 점수(0~1)", example = "0.9472") double score
) {

    public static ChatSearchChunkResponse from(SearchChunk chunk) {
        return new ChatSearchChunkResponse(
                chunk.importRunId(),
                chunk.importedPageId(),
                chunk.chunkIndex(),
                chunk.title(),
                chunk.sourceUrl(),
                chunk.content(),
                chunk.score()
        );
    }
}
