package com.knot.backend.workspace.application.dto.result;

import com.knot.backend.workspace.domain.ContentSourceAuthorizationOwnerType;
import com.knot.backend.workspace.domain.ContentSourceProvider;

public record AuthorizedContentSource(
        ContentSourceProvider provider,
        String accessCredential,
        String refreshCredential,
        String externalSourceId,
        String externalSourceName,
        String externalSourceIcon,
        String providerConnectionId,
        ContentSourceAuthorizationOwnerType authorizationOwnerType,
        String authorizationOwnerId,
        String externalTemplateId,
        String providerRequestId
) {
}
