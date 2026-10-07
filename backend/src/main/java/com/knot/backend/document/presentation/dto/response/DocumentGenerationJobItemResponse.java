package com.knot.backend.document.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.document.application.dto.result.DocumentGenerationJobItemResult;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DocumentGenerationJobItemResponse(
        @Schema(description = "DocumentGenerationJob ID", requiredMode = REQUIRED) long jobId,
        @Schema(description = "원본 RecordingSession ID", requiredMode = REQUIRED) long recordingSessionId,
        @Schema(description = "선택 입력 녹음 제목. 미입력이면 null", nullable = true, requiredMode = REQUIRED) String recordingTitle,
        @Schema(description = "작업 상태", allowableValues = {
                "QUEUED", "RUNNING", "FAILED"}, requiredMode = REQUIRED) DocumentGenerationJobStatus status,
        @Schema(description = "작업 생성 시각, UTC", requiredMode = REQUIRED) Instant createdAt,
        @Schema(description = "최근 작업 상태 갱신 시각, UTC", requiredMode = REQUIRED) Instant updatedAt
) {
    public static DocumentGenerationJobItemResponse from(DocumentGenerationJobItemResult result) {
        return new DocumentGenerationJobItemResponse(
                result.jobId(),
                result.recordingSessionId(),
                result.recordingTitle(),
                result.status(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
