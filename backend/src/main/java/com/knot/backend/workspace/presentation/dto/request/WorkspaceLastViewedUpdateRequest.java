package com.knot.backend.workspace.presentation.dto.request;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WorkspaceLastViewedUpdateRequest(
        @NotNull(message = "워크스페이스 ID는 필수입니다") @Positive(message = "워크스페이스 ID는 양수여야 합니다") Long workspaceId
) {

    @Schema(requiredMode = REQUIRED, example = "12", minimum = "1")
    public Long workspaceId() {
        return workspaceId;
    }
}
