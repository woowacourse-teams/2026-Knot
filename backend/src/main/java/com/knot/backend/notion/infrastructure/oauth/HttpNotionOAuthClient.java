package com.knot.backend.notion.infrastructure.oauth;

import com.knot.backend.notion.application.NotionOAuthClient;
import com.knot.backend.notion.application.dto.result.NotionOAuthToken;
import com.knot.backend.notion.domain.NotionErrorCode;
import com.knot.backend.notion.domain.NotionException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class HttpNotionOAuthClient implements NotionOAuthClient {
    private static final String AUTHORIZATION_CODE_GRANT = "authorization_code";
    private static final String USER_OWNER_TYPE = "user";
    private static final String WORKSPACE_OWNER_TYPE = "workspace";

    private final NotionOAuthProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String authorizationHeader;

    public HttpNotionOAuthClient(
            NotionOAuthProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        validateConfiguration(
                properties,
                httpClient,
                objectMapper
        );
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.authorizationHeader = basicAuthorization(
                properties.clientId(),
                properties.clientSecret()
        );
    }

    @Override
    public URI createAuthorizationUri(
            String state,
            URI callbackUri
    ) {
        if (state == null || state.isBlank() || callbackUri == null) {
            throw new NotionException(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
        }
        return UriComponentsBuilder.fromUri(properties.authorizationUri())
                .queryParam(
                        "client_id",
                        properties.clientId()
                )
                .queryParam(
                        "response_type",
                        "code"
                )
                .queryParam(
                        "owner",
                        "user"
                )
                .queryParam(
                        "redirect_uri",
                        callbackUri
                )
                .queryParam(
                        "state",
                        state
                )
                .build()
                .encode()
                .toUri();
    }

    @Override
    public NotionOAuthToken exchange(
            String code,
            URI callbackUri
    ) {
        if (code == null || code.isBlank() || callbackUri == null) {
            throw exchangeFailed();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(properties.tokenUri())
                    .timeout(properties.requestTimeout())
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            authorizationHeader
                    )
                    .header(
                            HttpHeaders.ACCEPT,
                            MediaType.APPLICATION_JSON_VALUE
                    )
                    .header(
                            HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE
                    )
                    .header(
                            "Notion-Version",
                            properties.apiVersion()
                    )
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    tokenRequestBody(
                                            code,
                                            callbackUri
                                    )
                            )
                    )
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw exchangeFailed();
            }
            return parseToken(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
            throw exchangeFailed(exception);
        } catch (JacksonException exception) {
            throw exchangeFailed();
        } catch (IOException exception) {
            throw exchangeFailed(exception);
        }
    }

    private String tokenRequestBody(
            String code,
            URI callbackUri
    ) throws JacksonException {
        Map<String, String> request = new LinkedHashMap<>();
        request.put(
                "grant_type",
                AUTHORIZATION_CODE_GRANT
        );
        request.put(
                "code",
                code
        );
        request.put(
                "redirect_uri",
                callbackUri.toString()
        );
        return objectMapper.writeValueAsString(request);
    }

    private NotionOAuthToken parseToken(String responseBody) throws JacksonException {
        JsonNode response = objectMapper.readTree(responseBody);
        if (!"bearer".equalsIgnoreCase(
                requiredText(
                        response,
                        "token_type"
                )
        )) {
            throw exchangeFailed();
        }
        Owner owner = parseOwner(response);
        return new NotionOAuthToken(
                requiredText(
                        response,
                        "access_token"
                ),
                nullableText(
                        response,
                        "refresh_token"
                ),
                requiredText(
                        response,
                        "workspace_id"
                ),
                nullableText(
                        response,
                        "workspace_name"
                ),
                optionalNullableText(
                        response,
                        "workspace_icon"
                ),
                requiredText(
                        response,
                        "bot_id"
                ),
                owner.type(),
                owner.userId(),
                optionalNullableText(
                        response,
                        "duplicated_template_id"
                ),
                optionalNullableText(
                        response,
                        "request_id"
                )
        );
    }

    private Owner parseOwner(JsonNode response) {
        JsonNode owner = response.get("owner");
        if (owner == null || owner.isNull() || !owner.isObject()) {
            throw exchangeFailed();
        }
        String ownerType = requiredText(
                owner,
                "type"
        );
        if (USER_OWNER_TYPE.equals(ownerType)) {
            JsonNode user = owner.get(USER_OWNER_TYPE);
            if (user == null || user.isNull() || !user.isObject()) {
                throw exchangeFailed();
            }
            return new Owner(
                    ownerType,
                    requiredText(
                            user,
                            "id"
                    )
            );
        }
        if (WORKSPACE_OWNER_TYPE.equals(ownerType)) {
            return new Owner(
                    ownerType,
                    null
            );
        }
        throw exchangeFailed();
    }

    private String requiredText(
            JsonNode response,
            String fieldName
    ) {
        if (response == null) {
            throw exchangeFailed();
        }
        JsonNode value = response.get(fieldName);
        if (value == null || value.isNull() || !value.isString()) {
            throw exchangeFailed();
        }
        String text = value.asString();
        if (text.isBlank()) {
            throw exchangeFailed();
        }
        return text;
    }

    private String nullableText(
            JsonNode response,
            String fieldName
    ) {
        if (response == null) {
            throw exchangeFailed();
        }
        JsonNode value = response.get(fieldName);
        if (value == null) {
            throw exchangeFailed();
        }
        if (value.isNull()) {
            return null;
        }
        if (!value.isString() || value.asString()
                .isBlank()) {
            throw exchangeFailed();
        }
        return value.asString();
    }

    private String optionalNullableText(
            JsonNode response,
            String fieldName
    ) {
        if (response == null) {
            throw exchangeFailed();
        }
        JsonNode value = response.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString() || value.asString()
                .isBlank()) {
            throw exchangeFailed();
        }
        return value.asString();
    }

    private void validateConfiguration(
            NotionOAuthProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        if (properties == null || httpClient == null || objectMapper == null || isBlank(properties.clientId())
                || isBlank(properties.clientSecret()) || properties.authorizationUri() == null
                || properties.tokenUri() == null || properties.requestTimeout() == null
                || !isPositive(properties.requestTimeout())) {
            throw new NotionException(NotionErrorCode.NOTION_OAUTH_CONFIGURATION_INVALID);
        }
    }

    private boolean isPositive(Duration duration) {
        return !duration.isZero() && !duration.isNegative();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String basicAuthorization(
            String clientId,
            String clientSecret
    ) {
        String credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private NotionException exchangeFailed() {
        return new NotionException(NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED);
    }

    private NotionException exchangeFailed(Throwable cause) {
        return new NotionException(
                NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED,
                cause
        );
    }

    private record Owner(
            String type,
            String userId
    ) {
    }
}
