package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.NotionConnectionStatusResult;
import com.knot.backend.workspace.domain.NotionConnectionStatus;

public record NotionConnectionStatusResponse(NotionConnectionStatus status) {

    public static NotionConnectionStatusResponse from(NotionConnectionStatusResult result) {
        return new NotionConnectionStatusResponse(result.status());
    }
}
