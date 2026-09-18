package com.knot.backend.workspace.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record WorkspaceInvitationAcceptanceRequest(@NotBlank(message = "초대 코드 또는 링크는 필수입니다") String credential) {
}
