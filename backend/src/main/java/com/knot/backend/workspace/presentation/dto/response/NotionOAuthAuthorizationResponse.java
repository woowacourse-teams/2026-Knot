package com.knot.backend.workspace.presentation.dto.response;

import com.knot.backend.workspace.application.dto.result.ContentSourceAuthorizationResult;

public record NotionOAuthAuthorizationResponse(String authorizationUrl) {

    public static NotionOAuthAuthorizationResponse from(ContentSourceAuthorizationResult result) {
        return new NotionOAuthAuthorizationResponse(
                result.authorizationUri()
                        .toString()
        );
    }
}
