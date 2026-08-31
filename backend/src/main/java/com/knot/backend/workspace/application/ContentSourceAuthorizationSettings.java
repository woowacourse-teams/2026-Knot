package com.knot.backend.workspace.application;

import java.net.URI;
import java.time.Duration;

public interface ContentSourceAuthorizationSettings {

    Duration stateTtl();

    URI callbackUri();

    URI successRedirectUri(Long workspaceId);

    URI failureRedirectUri(Long workspaceId);
}
