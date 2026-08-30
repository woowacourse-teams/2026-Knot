package com.knot.backend.workspace.presentation.dto.request;

import com.knot.backend.workspace.domain.Workspace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WorkspaceCreateRequest(
        @NotBlank @Size(max = Workspace.MAX_NAME_LENGTH) @Pattern(regexp = "^[가-힣A-Za-z ]+$") String name
) {
}
