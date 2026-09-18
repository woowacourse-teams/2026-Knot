package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.ContentSourceConnectionStatusResult;
import com.knot.backend.workspace.domain.ContentSourceConnectionStatus;

public record NotionConnectionStatusResponse(ContentSourceConnectionStatus status) {

    public static NotionConnectionStatusResponse from(ContentSourceConnectionStatusResult result) {
        return new NotionConnectionStatusResponse(result.status());
    }
}
