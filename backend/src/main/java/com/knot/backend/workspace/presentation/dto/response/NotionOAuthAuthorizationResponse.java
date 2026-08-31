package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.NotionOAuthAuthorizationResult;

public record NotionOAuthAuthorizationResponse(String authorizationUrl) {

    public static NotionOAuthAuthorizationResponse from(NotionOAuthAuthorizationResult result) {
        return new NotionOAuthAuthorizationResponse(
                result.authorizationUri()
                        .toString()
        );
    }
}
