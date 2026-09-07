package com.knot.backend.chat.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.knot.backend.chat.application.dto.result.ChatSearchResult;
import com.knot.backend.search.domain.SearchResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "탐색 검색 응답. READY면 groundingRules·chunks, 아니면 assistantMessageId·fallbackAnswer가 있다")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatSearchResponse(
        @Schema(description = "검색 결과 상태", example = "READY") SearchResultStatus status,
        @Schema(description = "저장된 USER 메시지 ID", example = "101") long userMessageId,
        @Schema(description = "사용자 LLM system 프롬프트 앞에 그대로 붙일 근거 규칙 문장(READY일 때만)") String groundingRules,
        @Schema(description = "점수 내림차순 근거 청크(최대 top-k, READY일 때만)") List<ChatSearchChunkResponse> chunks,
        @Schema(description = "서버가 저장한 안내 답변 메시지 ID(READY가 아닐 때만)", example = "102") Long assistantMessageId,
        @Schema(description = "서버가 저장한 안내 답변 본문(READY가 아닐 때만)") String fallbackAnswer
) {

    public static ChatSearchResponse from(ChatSearchResult result) {
        if (result.isReady()) {
            return new ChatSearchResponse(
                    result.status(),
                    result.userMessageId(),
                    result.groundingRules(),
                    result.chunks()
                            .stream()
                            .map(ChatSearchChunkResponse::from)
                            .toList(),
                    null,
                    null
            );
        }
        return new ChatSearchResponse(
                result.status(),
                result.userMessageId(),
                null,
                null,
                result.assistantMessageId(),
                result.fallbackAnswer()
        );
    }
}
