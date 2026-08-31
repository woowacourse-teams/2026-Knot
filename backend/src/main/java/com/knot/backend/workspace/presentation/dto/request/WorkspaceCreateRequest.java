package com.knot.backend.workspace.presentation.dto.request;

import static com.knot.backend.workspace.domain.Workspace.MAX_NAME_LENGTH;
import static com.knot.backend.workspace.domain.Workspace.NAME_PATTERN_REGEX;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

public record WorkspaceCreateRequest(
        @Schema(requiredMode = REQUIRED, maxLength = MAX_NAME_LENGTH, pattern = NAME_PATTERN_REGEX) String name
) {
}
