package com.knot.backend.document.presentation.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.knot.backend.document.application.dto.result.DocumentCardResult;
import com.knot.backend.document.domain.DocumentStatus;
import com.knot.backend.document.domain.MyConfirmationState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DocumentCardResponse(
        @Schema(description = "Document ID", requiredMode = REQUIRED) long id,
        @Schema(description = "원본 RecordingSession ID", requiredMode = REQUIRED) long recordingSessionId,
        @Schema(description = "AI가 분류한 주제 폴더 이름", requiredMode = REQUIRED) String topic,
        @Schema(description = "읽기 전용 제목", requiredMode = REQUIRED) String title,
        @Schema(description = "AI 요약. 없으면 null", nullable = true, requiredMode = REQUIRED) String summary,
        @Schema(description = "문서 상태", requiredMode = REQUIRED) DocumentStatus status,
        @Schema(description = "생성 시각, UTC", requiredMode = REQUIRED) Instant createdAt,
        @Schema(description = "일시정지를 제외한 서버 누적 녹음 길이. 소수 초는 버림", requiredMode = REQUIRED) int recordingDurationSeconds,
        @Schema(description = "PENDING=대상 미확인, CONFIRMED=확인 완료, NOT_REQUIRED=비대상", requiredMode = REQUIRED) MyConfirmationState myConfirmationState,
        @Schema(description = "확인·미확인·제외 대상 집계", requiredMode = REQUIRED) DocumentConfirmationSummaryResponse confirmationSummary
) {
    public static DocumentCardResponse from(DocumentCardResult result) {
        return new DocumentCardResponse(
                result.id(),
                result.recordingSessionId(),
                result.topic(),
                result.title(),
                result.summary(),
                result.status(),
                result.createdAt(),
                result.recordingDurationSeconds(),
                result.myConfirmationState(),
                DocumentConfirmationSummaryResponse.from(result.confirmationSummary())
        );
    }
}
