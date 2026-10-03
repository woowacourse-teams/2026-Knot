package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.WorkspaceOwnershipTransferCandidateResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "워크스페이스 OWNER 승계 후보")
public record WorkspaceOwnershipTransferCandidateResponse(
        @Schema(description = "활성 MEMBER의 ID", example = "2") long memberId,
        @Schema(description = "멤버 닉네임", example = "루덴스") String nickname
) {

    public static WorkspaceOwnershipTransferCandidateResponse from(WorkspaceOwnershipTransferCandidateResult result) {
        return new WorkspaceOwnershipTransferCandidateResponse(
                result.memberId(),
                result.nickname()
        );
    }
}
