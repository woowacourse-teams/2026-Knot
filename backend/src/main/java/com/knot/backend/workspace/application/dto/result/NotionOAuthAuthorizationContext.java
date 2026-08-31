package com.knot.backend.workspace.application.dto.result;

import java.net.URI;

public record NotionOAuthAuthorizationContext(
        Long authorizationId,
        Long workspaceId,
        Long authorizingMemberId,
        URI callbackUri
) {
}
