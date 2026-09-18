package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.AuthorizedContentSource;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.net.URI;

public interface ContentSourceAuthorizationClient {

    ContentSourceProvider provider();

    URI createAuthorizationUri(
            ContentSourceProvider provider,
            String state,
            URI callbackUri
    );

    AuthorizedContentSource exchange(
            ContentSourceProvider provider,
            String code,
            URI callbackUri
    );
}
