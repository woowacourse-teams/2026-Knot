package com.knot.backend.document.presentation.dto.response;

import com.knot.backend.document.application.dto.result.DocumentTranscriptResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

public record DocumentTranscriptResponse(
        @Schema(description = "문서에 연결된 전사 원문 ID", requiredMode = RequiredMode.REQUIRED) long transcriptId,
        @Schema(description = "현재 최종 파일 하나 업로드 범위에서는 false", requiredMode = RequiredMode.REQUIRED) boolean isPartial,
        @Schema(description = "원본 녹음 길이(초)", requiredMode = RequiredMode.REQUIRED) int recordingDurationSeconds,
        @Schema(description = "저장된 전체 원문", requiredMode = RequiredMode.REQUIRED) String transcriptText,
        @ArraySchema(minItems = 1, schema = @Schema(implementation = DocumentTranscriptSegmentResponse.class)) @Schema(description = "시작 시각·저장 순서로 정렬된 실제 발화 구간", requiredMode = RequiredMode.REQUIRED) List<DocumentTranscriptSegmentResponse> segments
) {

    public DocumentTranscriptResponse {
        segments = List.copyOf(segments);
    }

    public static DocumentTranscriptResponse from(DocumentTranscriptResult result) {
        return new DocumentTranscriptResponse(
                result.transcriptId(),
                false,
                result.recordingDurationSeconds(),
                result.transcriptText(),
                result.segments()
                        .stream()
                        .map(DocumentTranscriptSegmentResponse::from)
                        .toList()
        );
    }
}
