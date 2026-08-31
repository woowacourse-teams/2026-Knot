package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ContentSourceAuthorizationContext;
import com.knot.backend.workspace.application.dto.result.AuthorizedContentSource;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.domain.WorkspaceException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ContentSourceCallbackService {
    private final ContentSourceAuthorizationService authorizationService;
    private final ContentSourceAuthorizationClient authorizationClient;
    private final ContentSourceConnectionService connectionService;

    public boolean complete(
            ContentSourceProvider provider,
            String code,
            String state,
            String error
    ) {
        try {
            ContentSourceAuthorizationContext authorization = authorizationService.consume(
                    provider,
                    state
            );
            if (hasText(error) || !hasText(code)) {
                return false;
            }
            AuthorizedContentSource authorizedContentSource = authorizationClient.exchange(
                    provider,
                    code,
                    authorization.callbackUri()
            );
            connectionService.connect(
                    authorization,
                    authorizedContentSource
            );
            return true;
        } catch (ContentSourceException | WorkspaceException exception) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
