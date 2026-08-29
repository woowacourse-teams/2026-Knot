package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "워크스페이스 초대 응답")
public record WorkspaceInvitationResponse(
        @Schema(description = "초대 코드", example = "ABCD2345") String code,
        @Schema(description = "초대 링크 토큰", example = "R0R1nWqF5wvJ5WUpmDUW0Czt0bbR9weqbQEWY5NSP_E") String linkToken,
        @Schema(description = "초대 만료 시각", example = "2026-08-30T00:00:00Z") Instant expiresAt
) {

    public static WorkspaceInvitationResponse from(WorkspaceInvitationResult result) {
        return new WorkspaceInvitationResponse(
                result.code(),
                result.linkToken(),
                result.expiresAt()
        );
    }
}
