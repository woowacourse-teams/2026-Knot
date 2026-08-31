package com.knot.backend.workspace.infrastructure.notion.oauth;

import com.knot.backend.workspace.application.ContentSourceAuthorizationSettings;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notion.oauth")
public record NotionOAuthProperties(
        String clientId,
        String clientSecret,
        URI authorizationUri,
        URI tokenUri,
        String apiVersion,
        URI callbackUri,
        URI successRedirectUri,
        URI failureRedirectUri,
        Duration stateTtl,
        Duration requestTimeout,
        String activeEncryptionKeyVersion,
        String stateHashKey,
        Map<String, String> encryptionKeys
) implements ContentSourceAuthorizationSettings {

    public NotionOAuthProperties {
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(apiVersion) || !isSecureOAuthUri(authorizationUri)
                || !isSecureOAuthUri(tokenUri) || !isSecureOAuthUri(callbackUri)
                || !isUsableRedirect(successRedirectUri) || !isUsableRedirect(failureRedirectUri)
                || !isPositive(stateTtl) || !isPositive(requestTimeout) || isBlank(activeEncryptionKeyVersion)
                || isBlank(stateHashKey) || encryptionKeys == null || encryptionKeys.isEmpty()) {
            throw new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_CONFIGURATION_INVALID);
        }
        encryptionKeys = Map.copyOf(encryptionKeys);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isSecureOAuthUri(URI value) {
        if (value == null || !value.isAbsolute() || value.getUserInfo() != null || value.getHost() == null) {
            return false;
        }
        String scheme = value.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return true;
        }
        return "http".equalsIgnoreCase(scheme) && isLocalhost(value.getHost());
    }

    private static boolean isLocalhost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private static boolean isUsableRedirect(URI value) {
        return value != null && !value.toString()
                .isBlank();
    }

    private static boolean isPositive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    @Override
    public String toString() {
        return "NotionOAuthProperties[redacted]";
    }
}
