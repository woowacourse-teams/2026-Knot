package com.knot.backend.notion.presentation.dto.response;

import com.knot.backend.notion.application.dto.result.NotionOAuthAuthorizationResult;

public record NotionOAuthAuthorizationResponse(String authorizationUrl) {

    public static NotionOAuthAuthorizationResponse from(NotionOAuthAuthorizationResult result) {
        return new NotionOAuthAuthorizationResponse(
                result.authorizationUri()
                        .toString()
        );
    }
}
