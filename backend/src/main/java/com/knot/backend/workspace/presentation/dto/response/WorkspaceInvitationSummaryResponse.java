package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "유효한 초대의 상태 정보. 코드·링크 원문과 암호문은 제공하지 않습니다")
public record WorkspaceInvitationSummaryResponse(
        @Schema(description = "초대 ID", example = "1") Long invitationId,
        @Schema(description = "초대 생성 시각", example = "2026-10-03T00:00:00Z") Instant createdAt,
        @Schema(description = "코드·링크 공통 만료 시각", example = "2026-10-04T00:00:00Z") Instant expiresAt
) {
    public static WorkspaceInvitationSummaryResponse from(WorkspaceInvitationSummaryResult result) {
        return new WorkspaceInvitationSummaryResponse(
                result.invitationId(),
                result.createdAt(),
                result.expiresAt()
        );
    }
}
