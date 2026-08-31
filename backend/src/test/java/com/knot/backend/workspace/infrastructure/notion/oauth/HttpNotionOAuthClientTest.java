package com.knot.backend.workspace.infrastructure.notion.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.AuthorizedContentSource;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationOwnerType;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class HttpNotionOAuthClientTest {
    private static final URI CALLBACK_URI = URI.create("https://api.example.com/api/v1/notion/oauth/callback");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @DisplayName("authorization URL에 Notion Public OAuth 필수 값과 state를 담는다")
    @Test
    void createAuthorizationUri_success() {
        // given
        HttpNotionOAuthClient client = client(
                URI.create("http://localhost/token"),
                Duration.ofSeconds(1)
        );

        // when
        URI authorizationUri = client.createAuthorizationUri(
                ContentSourceProvider.NOTION,
                "raw-state",
                CALLBACK_URI
        );

        // then
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(authorizationUri)
                .build()
                .getQueryParams();
        assertThat(client.provider()).isEqualTo(ContentSourceProvider.NOTION);
        assertThat(authorizationUri.getPath()).isEqualTo("/v1/oauth/authorize");
        assertThat(query.getFirst("client_id")).isEqualTo("client-id");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("owner")).isEqualTo("user");
        assertThat(query.getFirst("redirect_uri")).isEqualTo(CALLBACK_URI.toString());
        assertThat(query.getFirst("state")).isEqualTo("raw-state");
    }

    @DisplayName("authorization code를 Basic 인증과 JSON body로 교환한다")
    @Test
    void exchange_success() throws Exception {
        // given
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> notionVersion = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        URI tokenUri = startServer(exchange -> {
            authorization.set(
                    exchange.getRequestHeaders()
                            .getFirst(HttpHeaders.AUTHORIZATION)
            );
            notionVersion.set(
                    exchange.getRequestHeaders()
                            .getFirst("Notion-Version")
            );
            requestBody.set(
                    new String(
                            exchange.getRequestBody()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    )
            );
            respond(
                    exchange,
                    200,
                    """
                            {
                              "access_token":"access-secret",
                              "token_type":"bearer",
                              "refresh_token":"refresh-secret",
                              "workspace_id":"notion-workspace",
                              "workspace_name":"Knot Notion",
                              "workspace_icon":"https://static.notion.test/icon.png",
                              "bot_id":"bot-id",
                              "owner":{
                                "type":"user",
                                "user":{
                                  "id":"notion-owner-user-id",
                                  "name":"PII name",
                                  "avatar_url":"https://static.notion.test/avatar.png",
                                  "person":{
                                    "email":"owner@example.com"
                                  }
                                }
                              },
                              "duplicated_template_id":"template-id",
                              "request_id":"request-id"
                            }
                            """
            );
        });
        HttpNotionOAuthClient client = client(
                tokenUri,
                Duration.ofSeconds(1)
        );

        // when
        AuthorizedContentSource token = client.exchange(
                ContentSourceProvider.NOTION,
                "authorization-code",
                CALLBACK_URI
        );

        // then
        String expectedBasic = "Basic " + Base64.getEncoder()
                .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));
        assertThat(authorization.get()).isEqualTo(expectedBasic);
        assertThat(notionVersion.get()).isEqualTo("2026-03-11");
        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(
                body.get("grant_type")
                        .asString()
        ).isEqualTo("authorization_code");
        assertThat(
                body.get("code")
                        .asString()
        ).isEqualTo("authorization-code");
        assertThat(
                body.get("redirect_uri")
                        .asString()
        ).isEqualTo(CALLBACK_URI.toString());
        assertThat(token).isEqualTo(
                new AuthorizedContentSource(
                        ContentSourceProvider.NOTION,
                        "access-secret",
                        "refresh-secret",
                        "notion-workspace",
                        "Knot Notion",
                        "https://static.notion.test/icon.png",
                        "bot-id",
                        ContentSourceAuthorizationOwnerType.USER,
                        "notion-owner-user-id",
                        "template-id",
                        "request-id"
                )
        );
    }

    @DisplayName("workspace owner 응답은 owner user id 없이도 token으로 변환한다")
    @Test
    void exchange_success_workspaceOwner() throws Exception {
        // given
        URI tokenUri = startServer(
                exchange -> respond(
                        exchange,
                        200,
                        """
                                {
                                  "access_token":"access-secret",
                                  "token_type":"bearer",
                                  "refresh_token":null,
                                  "workspace_id":"notion-workspace",
                                  "workspace_name":null,
                                  "bot_id":"bot-id",
                                  "owner":{
                                    "type":"workspace",
                                    "workspace":true
                                  }
                                }
                                """
                )
        );
        HttpNotionOAuthClient client = client(
                tokenUri,
                Duration.ofSeconds(1)
        );

        // when
        AuthorizedContentSource token = client.exchange(
                ContentSourceProvider.NOTION,
                "authorization-code",
                CALLBACK_URI
        );

        // then
        assertThat(token).isEqualTo(
                new AuthorizedContentSource(
                        ContentSourceProvider.NOTION,
                        "access-secret",
                        null,
                        "notion-workspace",
                        null,
                        null,
                        "bot-id",
                        ContentSourceAuthorizationOwnerType.WORKSPACE,
                        null,
                        null,
                        null
                )
        );
    }

    @DisplayName("Notion token endpoint의 4xx 응답은 기존 연결을 대체할 수 없는 교환 실패로 변환한다")
    @Test
    void exchange_failure_clientError() throws Exception {
        // given
        URI tokenUri = startServer(
                exchange -> respond(
                        exchange,
                        400,
                        "{\"error\":\"invalid_grant\",\"secret\":\"provider-secret\"}"
                )
        );
        HttpNotionOAuthClient client = client(
                tokenUri,
                Duration.ofSeconds(1)
        );

        // when
        Throwable thrown = catchThrowable(
                () -> client.exchange(
                        ContentSourceProvider.NOTION,
                        "sensitive-code",
                        CALLBACK_URI
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                ContentSourceException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ContentSourceErrorCode.CONTENT_SOURCE_AUTHORIZATION_FAILED);
                    assertThat(exception.getMessage()).doesNotContain(
                            "sensitive-code",
                            "provider-secret"
                    );
                }
        );
    }

    @DisplayName("Notion token endpoint의 5xx 응답은 token 내용을 노출하지 않는 교환 실패로 변환한다")
    @Test
    void exchange_failure_serverError() throws Exception {
        // given
        URI tokenUri = startServer(
                exchange -> respond(
                        exchange,
                        503,
                        "{\"message\":\"provider-secret\"}"
                )
        );
        HttpNotionOAuthClient client = client(
                tokenUri,
                Duration.ofSeconds(1)
        );

        // when
        Throwable thrown = catchThrowable(
                () -> client.exchange(
                        ContentSourceProvider.NOTION,
                        "sensitive-code",
                        CALLBACK_URI
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                ContentSourceException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ContentSourceErrorCode.CONTENT_SOURCE_AUTHORIZATION_FAILED);
                    assertThat(exception.getMessage()).doesNotContain(
                            "sensitive-code",
                            "provider-secret"
                    );
                }
        );
    }

    @DisplayName("Notion token endpoint가 제한 시간 안에 응답하지 않으면 교환을 중단한다")
    @Test
    void exchange_failure_timeout() throws Exception {
        // given
        URI tokenUri = startServer(exchange -> {
            try {
                Thread.sleep(250);
                respond(
                        exchange,
                        200,
                        "{}"
                );
            } catch (InterruptedException exception) {
                Thread.currentThread()
                        .interrupt();
            }
        });
        HttpNotionOAuthClient client = client(
                tokenUri,
                Duration.ofMillis(50)
        );

        // when
        Throwable thrown = catchThrowable(
                () -> client.exchange(
                        ContentSourceProvider.NOTION,
                        "authorization-code",
                        CALLBACK_URI
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                ContentSourceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ContentSourceErrorCode.CONTENT_SOURCE_AUTHORIZATION_FAILED)
        );
    }

    @DisplayName("성공 응답에 필수 token 필드가 없으면 교환을 거부한다")
    @Test
    void exchange_failure_missingResponseField() throws Exception {
        // given
        URI tokenUri = startServer(
                exchange -> respond(
                        exchange,
                        200,
                        "{\"workspace_id\":\"workspace\",\"bot_id\":\"bot\"}"
                )
        );
        HttpNotionOAuthClient client = client(
                tokenUri,
                Duration.ofSeconds(1)
        );

        // when
        Throwable thrown = catchThrowable(
                () -> client.exchange(
                        ContentSourceProvider.NOTION,
                        "authorization-code",
                        CALLBACK_URI
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                ContentSourceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ContentSourceErrorCode.CONTENT_SOURCE_AUTHORIZATION_FAILED)
        );
    }

    @DisplayName("owner user 응답에 user id가 없으면 교환을 거부한다")
    @Test
    void exchange_failure_missingOwnerUserId() throws Exception {
        // given
        URI tokenUri = startServer(
                exchange -> respond(
                        exchange,
                        200,
                        """
                                {
                                  "access_token":"access-secret",
                                  "token_type":"bearer",
                                  "refresh_token":null,
                                  "workspace_id":"notion-workspace",
                                  "workspace_name":null,
                                  "bot_id":"bot-id",
                                  "owner":{
                                    "type":"user",
                                    "user":{
                                      "name":"PII name"
                                    }
                                  }
                                }
                                """
                )
        );
        HttpNotionOAuthClient client = client(
                tokenUri,
                Duration.ofSeconds(1)
        );

        // when
        Throwable thrown = catchThrowable(
                () -> client.exchange(
                        ContentSourceProvider.NOTION,
                        "authorization-code",
                        CALLBACK_URI
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                ContentSourceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ContentSourceErrorCode.CONTENT_SOURCE_AUTHORIZATION_FAILED)
        );
    }

    @DisplayName("Notion 성공 응답이 올바른 JSON이 아니면 원인을 보존한 교환 실패로 변환한다")
    @Test
    void exchange_failure_malformedJsonPreservesCause() throws Exception {
        // given
        URI tokenUri = startServer(
                exchange -> respond(
                        exchange,
                        200,
                        "{malformed-json"
                )
        );
        HttpNotionOAuthClient client = client(
                tokenUri,
                Duration.ofSeconds(1)
        );

        // when
        Throwable thrown = catchThrowable(
                () -> client.exchange(
                        ContentSourceProvider.NOTION,
                        "authorization-code",
                        CALLBACK_URI
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                ContentSourceException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ContentSourceErrorCode.CONTENT_SOURCE_AUTHORIZATION_FAILED);
                    assertThat(exception.getCause()).isInstanceOf(JacksonException.class);
                }
        );
    }

    private HttpNotionOAuthClient client(
            URI tokenUri,
            Duration requestTimeout
    ) {
        NotionOAuthProperties properties = mock(NotionOAuthProperties.class);
        when(properties.clientId()).thenReturn("client-id");
        when(properties.clientSecret()).thenReturn("client-secret");
        when(properties.authorizationUri()).thenReturn(URI.create("https://api.notion.com/v1/oauth/authorize"));
        when(properties.tokenUri()).thenReturn(tokenUri);
        when(properties.apiVersion()).thenReturn("2026-03-11");
        when(properties.requestTimeout()).thenReturn(requestTimeout);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .build();
        return new HttpNotionOAuthClient(
                properties,
                httpClient,
                objectMapper
        );
    }

    private URI startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(
                        "127.0.0.1",
                        0
                ),
                0
        );
        server.createContext(
                "/v1/oauth/token",
                exchange -> {
                    try (exchange) {
                        handler.handle(exchange);
                    }
                }
        );
        server.start();
        return URI.create(
                "http://127.0.0.1:" + server.getAddress()
                        .getPort() + "/v1/oauth/token"
        );
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders()
                .set(
                        HttpHeaders.CONTENT_TYPE,
                        "application/json"
                );
        exchange.sendResponseHeaders(
                status,
                response.length
        );
        exchange.getResponseBody()
                .write(response);
    }

    @FunctionalInterface
    private interface ExchangeHandler {

        void handle(HttpExchange exchange) throws IOException;
    }
}
