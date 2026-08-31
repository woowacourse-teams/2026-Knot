package com.knot.backend.notion.application;

import java.net.URI;
import java.time.Duration;

public interface NotionOAuthSettings {

    Duration stateTtl();

    URI callbackUri();

    URI successRedirectUri();

    URI failureRedirectUri();
}
