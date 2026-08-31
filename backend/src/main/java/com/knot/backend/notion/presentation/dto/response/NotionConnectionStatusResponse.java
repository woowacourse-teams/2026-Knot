package com.knot.backend.notion.presentation.dto.response;

import com.knot.backend.notion.application.dto.result.NotionConnectionStatusResult;
import com.knot.backend.notion.domain.NotionConnectionStatus;

public record NotionConnectionStatusResponse(NotionConnectionStatus status) {

    public static NotionConnectionStatusResponse from(NotionConnectionStatusResult result) {
        return new NotionConnectionStatusResponse(result.status());
    }
}
