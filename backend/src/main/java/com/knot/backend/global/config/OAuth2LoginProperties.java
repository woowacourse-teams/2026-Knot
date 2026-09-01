package com.knot.backend.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.oauth2")
public class OAuth2LoginProperties {
    private String successRedirectUri = "/api/v1/auth/me";
    private String nicknameRedirectUri = "/onboarding";
    private String failureRedirectUri = "/login?error=oauth2";
}
