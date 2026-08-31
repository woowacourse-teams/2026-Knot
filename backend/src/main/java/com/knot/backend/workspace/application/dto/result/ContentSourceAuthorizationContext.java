package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.net.URI;

public record ContentSourceAuthorizationContext(
        Long authorizationId,
        Long workspaceId,
        ContentSourceProvider provider,
        Long authorizingMemberId,
        URI callbackUri
) {
}
