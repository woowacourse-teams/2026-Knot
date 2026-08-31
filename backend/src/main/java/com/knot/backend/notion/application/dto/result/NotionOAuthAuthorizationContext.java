package com.knot.backend.notion.application.dto.result;

import java.net.URI;

public record NotionOAuthAuthorizationContext(
        Long authorizationId,
        Long workspaceId,
        Long authorizingMemberId,
        URI callbackUri
) {
}
