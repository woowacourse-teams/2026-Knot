package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.NotionOAuthToken;
import java.net.URI;

public interface NotionOAuthClient {

    URI createAuthorizationUri(
            String state,
            URI callbackUri
    );

    NotionOAuthToken exchange(
            String code,
            URI callbackUri
    );
}
