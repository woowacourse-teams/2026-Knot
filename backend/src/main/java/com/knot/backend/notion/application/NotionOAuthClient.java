package com.knot.backend.notion.application;

import com.knot.backend.notion.application.dto.result.NotionOAuthToken;
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
