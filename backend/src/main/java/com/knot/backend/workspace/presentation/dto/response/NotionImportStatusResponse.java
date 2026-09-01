package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.ContentImportStatusResult;
import com.knot.backend.workspace.domain.ContentImportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Notion Import 실행 상태")
// @formatter:off
public record NotionImportStatusResponse(
        @Schema(description = "Import 실행 ID", example = "1") long id,
        @Schema(description = "Import 상태", example = "RUNNING") ContentImportStatus status,
        @Schema(
                description = "가져올 전체 Page 수, 아직 알 수 없으면 null",
                example = "30",
                nullable = true
        ) Integer totalPageCount,
        @Schema(description = "처리한 Page 수", example = "12") int processedPageCount,
        @Schema(
                description = "사용자 공개용 실패 사유, FAILED가 아니면 null",
                example = "Notion 문서를 가져오지 못했습니다",
                nullable = true
        ) String failureReason,
        @Schema(description = "Import 요청 시각", example = "2026-08-31T00:00:00Z") Instant createdAt,
        @Schema(
                description = "Import 시작 시각, 시작 전이면 null",
                example = "2026-08-31T00:00:01Z",
                nullable = true
        ) Instant startedAt,
        @Schema(
                description = "Import 종료 시각, 진행 중이면 null",
                example = "2026-08-31T00:01:00Z",
                nullable = true
        ) Instant completedAt
) {
    // @formatter:on
    private static final String FAILURE_REASON = "Notion 문서를 가져오지 못했습니다";

    public static NotionImportStatusResponse from(ContentImportStatusResult result) {
        return new NotionImportStatusResponse(
                result.id(),
                result.status(),
                result.totalPageCount(),
                result.processedPageCount(),
                failureReason(result.status()),
                result.createdAt(),
                result.startedAt(),
                result.completedAt()
        );
    }

    private static String failureReason(ContentImportStatus status) {
        if (status == ContentImportStatus.FAILED) {
            return FAILURE_REASON;
        }
        return null;
    }
}
