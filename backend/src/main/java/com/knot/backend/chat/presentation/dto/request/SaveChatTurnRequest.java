package com.knot.backend.chat.presentation.dto.request;

import com.knot.backend.chat.application.dto.command.SaveChatTurnCommand;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "턴 저장 요청. CLI 에이전트가 만든 질문·답변·근거를 한 번에 저장한다")
public record SaveChatTurnRequest(
        @Schema(description = "사용자 질문", example = "PostgreSQL을 왜 사용했나요?", maxLength = 10_000) @NotBlank(message = "질문은 비어 있을 수 없습니다") @Size(max = 10_000, message = "질문은 10000자 이하여야 합니다") String question,
        @Schema(description = "에이전트가 만든 답변") @NotBlank(message = "답변은 비어 있을 수 없습니다") String answer,
        @ArraySchema(
                schema = @Schema(implementation = SaveChatTurnReferenceRequest.class),
                arraySchema = @Schema(description = "근거 목록(최대 8개). 배열 순서가 rank다"),
                maxItems = 8
        ) @NotNull(message = "references는 필수입니다") @Size(max = 8, message = "근거는 8개 이하여야 합니다") List<@Valid @NotNull(message = "근거 항목은 null일 수 없습니다") SaveChatTurnReferenceRequest> references
) {

    public SaveChatTurnCommand toCommand() {
        return new SaveChatTurnCommand(
                question,
                answer,
                references.stream()
                        .map(SaveChatTurnReferenceRequest::toCandidate)
                        .toList()
        );
    }
}
