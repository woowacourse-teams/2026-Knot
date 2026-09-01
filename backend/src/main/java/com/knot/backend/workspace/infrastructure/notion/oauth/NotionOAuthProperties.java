package com.knot.backend.workspace.infrastructure.notion.oauth;

import com.knot.backend.workspace.application.ContentSourceAuthorizationSettings;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.util.UriComponentsBuilder;

@ConfigurationProperties(prefix = "notion.oauth")
public record NotionOAuthProperties(
        String clientId,
        String clientSecret,
        URI authorizationUri,
        URI tokenUri,
        String apiVersion,
        URI callbackUri,
        URI redirectBaseUri,
        Duration stateTtl,
        Duration requestTimeout,
        String activeEncryptionKeyVersion,
        String stateHashKey,
        Map<String, String> encryptionKeys
) implements ContentSourceAuthorizationSettings {

    public NotionOAuthProperties {
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(apiVersion) || !isSecureOAuthUri(authorizationUri)
                || !isSecureTokenUri(tokenUri) || !isSecureOAuthUri(callbackUri)
                || !isSecureRedirectBaseUri(redirectBaseUri) || !isPositive(stateTtl) || !isPositive(requestTimeout)
                || isBlank(activeEncryptionKeyVersion) || isBlank(stateHashKey) || encryptionKeys == null
                || encryptionKeys.isEmpty()) {
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

    private static boolean isSecureTokenUri(URI value) {
        return isAbsoluteHttpUri(value) && "https".equalsIgnoreCase(value.getScheme());
    }

    private static boolean isSecureRedirectBaseUri(URI value) {
        if (!isAbsoluteHttpUri(value) || value.getQuery() != null || value.getFragment() != null) {
            return false;
        }
        String path = value.getPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            return false;
        }
        return "https".equalsIgnoreCase(value.getScheme())
                || "http".equalsIgnoreCase(value.getScheme()) && isLocalhost(value.getHost());
    }

    private static boolean isAbsoluteHttpUri(URI value) {
        return value != null && value.isAbsolute() && value.getUserInfo() == null && value.getHost() != null;
    }

    private static boolean isLocalhost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private static boolean isPositive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    @Override
    public URI successRedirectUri(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_CONFIGURATION_INVALID);
        }
        return redirectUri(
                workspaceId,
                "connected"
        );
    }

    @Override
    public URI failureRedirectUri(Long workspaceId) {
        return redirectUri(
                workspaceId,
                "failed"
        );
    }

    private URI redirectUri(
            Long workspaceId,
            String result
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(redirectBaseUri);
        if (workspaceId == null || workspaceId <= 0) {
            builder.pathSegment("workspace");
        } else {
            builder.pathSegment(
                    "workspace",
                    workspaceId.toString(),
                    "notion-connection"
            );
        }
        return builder.queryParam(
                "result",
                result
        )
                .build()
                .encode()
                .toUri();
    }

    @Override
    public String toString() {
        return "NotionOAuthProperties[redacted]";
    }
}
