package com.knot.backend.chat.presentation.dto.request;

import com.knot.backend.search.domain.SearchReferenceCandidate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "턴 저장 근거 한 건. 검색 API(chunks)가 돌려준 값을 그대로 넘긴다")
public record SaveChatTurnReferenceRequest(
        @Schema(description = "검색 결과의 importRunId", example = "301") @NotNull(message = "importRunId는 필수입니다") @Positive(message = "importRunId는 양수여야 합니다") Long importRunId,
        @Schema(description = "검색 결과의 importedPageId", example = "201") @NotNull(message = "importedPageId는 필수입니다") @Positive(message = "importedPageId는 양수여야 합니다") Long importedPageId,
        @Schema(description = "검색 결과의 chunkIndex(0부터)", example = "2") @NotNull(message = "chunkIndex는 필수입니다") @Min(value = 0, message = "chunkIndex는 0 이상이어야 합니다") @Max(value = Short.MAX_VALUE, message = "chunkIndex가 너무 큽니다") Integer chunkIndex,
        @Schema(description = "검색 결과의 score. 0~1 밖이면 서버가 잘라 저장한다", example = "0.95") @NotNull(message = "score는 필수입니다") Double score
) {

    public SearchReferenceCandidate toCandidate() {
        return new SearchReferenceCandidate(
                importRunId,
                importedPageId,
                chunkIndex,
                score
        );
    }
}
