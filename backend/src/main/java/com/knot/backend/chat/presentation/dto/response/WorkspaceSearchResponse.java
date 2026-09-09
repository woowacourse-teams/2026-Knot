package com.knot.backend.chat.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.knot.backend.chat.application.dto.result.WorkspaceSearchResult;
import com.knot.backend.search.domain.SearchResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Workspace 검색 응답. READY면 groundingRules·chunks, 아니면 fallbackAnswer가 있다. 아무것도 저장하지 않는다")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkspaceSearchResponse(
        @Schema(description = "검색 결과 상태", example = "READY") SearchResultStatus status,
        @Schema(description = "에이전트가 답변 앞에 그대로 지켜야 할 근거 규칙 문장(READY일 때만)") String groundingRules,
        @Schema(description = "점수 내림차순 근거 청크(최대 top-k, READY일 때만)") List<ChatSearchChunkResponse> chunks,
        @Schema(description = "근거가 없거나 질문 범위가 넓을 때의 안내 문구(READY가 아닐 때만)") String fallbackAnswer
) {

    public static WorkspaceSearchResponse from(WorkspaceSearchResult result) {
        if (result.isReady()) {
            return new WorkspaceSearchResponse(
                    result.status(),
                    result.groundingRules(),
                    result.chunks()
                            .stream()
                            .map(ChatSearchChunkResponse::from)
                            .toList(),
                    null
            );
        }
        return new WorkspaceSearchResponse(
                result.status(),
                null,
                null,
                result.fallbackAnswer()
        );
    }
}
