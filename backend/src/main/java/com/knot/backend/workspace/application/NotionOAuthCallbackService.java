package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.NotionOAuthAuthorizationContext;
import com.knot.backend.workspace.application.dto.result.NotionOAuthToken;
import com.knot.backend.workspace.domain.NotionException;
import com.knot.backend.workspace.domain.WorkspaceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotionOAuthCallbackService {
    private final NotionOAuthAuthorizationService authorizationService;
    private final NotionOAuthClient oAuthClient;
    private final NotionConnectionService connectionService;

    public boolean complete(
            String code,
            String state,
            String error
    ) {
        try {
            NotionOAuthAuthorizationContext authorization = authorizationService.consume(state);
            if (hasText(error) || !hasText(code)) {
                return false;
            }
            NotionOAuthToken token = oAuthClient.exchange(
                    code,
                    authorization.callbackUri()
            );
            connectionService.connect(
                    authorization,
                    token
            );
            return true;
        } catch (NotionException | WorkspaceException exception) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
