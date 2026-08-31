package com.knot.backend.workspace.infrastructure.notion.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.workspace.domain.NotionErrorCode;
import com.knot.backend.workspace.domain.NotionException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotionOAuthPropertiesTest {
    private static final String STATE_HASH_KEY = "bm90aW9uLXN0YXRlLWhhc2gta2V5LTAwMDAwMDAwMDA";
    private static final String ENCRYPTION_KEY = "bm90aW9uLWVuY3J5cHRpb24ta2V5LTAwMDAwMDAwMDA";

    @DisplayName("설정 문자열은 client secret과 암호화 key를 노출하지 않는다")
    @Test
    void toString_success_redactsSecrets() {
        // given
        NotionOAuthProperties properties = validProperties();

        // when
        String description = properties.toString();

        // then
        assertThat(description).doesNotContain(
                "client-secret",
                STATE_HASH_KEY,
                ENCRYPTION_KEY
        );
    }

    @DisplayName("절대 경로가 아닌 callback URI 설정은 애플리케이션 시작 전에 거부한다")
    @Test
    void create_failure_relativeCallbackUri() {
        // given

        // when
        Throwable thrown = catchThrowable(
                () -> new NotionOAuthProperties(
                        "client-id",
                        "client-secret",
                        URI.create("https://api.notion.com/v1/oauth/authorize"),
                        URI.create("https://api.notion.com/v1/oauth/token"),
                        "2026-03-11",
                        URI.create("/api/v1/notion/oauth/callback"),
                        URI.create("/notion-connected"),
                        URI.create("/notion-failed"),
                        Duration.ofMinutes(10),
                        Duration.ofSeconds(5),
                        "v1",
                        STATE_HASH_KEY,
                        Map.of(
                                "v1",
                                ENCRYPTION_KEY
                        )
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                NotionException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(NotionErrorCode.NOTION_OAUTH_CONFIGURATION_INVALID)
        );
    }

    @DisplayName("HTTPS OAuth URI 설정은 허용한다")
    @Test
    void create_success_httpsOAuthUris() {
        // given

        // when
        NotionOAuthProperties properties = validProperties();

        // then
        assertThat(properties.authorizationUri()).hasScheme("https");
        assertThat(properties.tokenUri()).hasScheme("https");
        assertThat(properties.callbackUri()).hasScheme("https");
    }

    @DisplayName("localhost HTTP OAuth URI 설정은 개발 환경을 위해 허용한다")
    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1", "[::1]"})
    void create_success_localhostHttpOAuthUris(String host) {
        // given

        // when
        NotionOAuthProperties properties = properties(
                URI.create("http://" + host + "/v1/oauth/authorize"),
                URI.create("http://" + host + "/v1/oauth/token"),
                URI.create("http://" + host + "/api/v1/notion/oauth/callback")
        );

        // then
        assertThat(properties.authorizationUri()).hasScheme("http");
        assertThat(properties.tokenUri()).hasScheme("http");
        assertThat(properties.callbackUri()).hasScheme("http");
    }

    @DisplayName("비로컬 HTTP OAuth URI 설정은 거부한다")
    @ParameterizedTest
    @ValueSource(strings = {"authorizationUri", "tokenUri", "callbackUri"})
    void create_failure_plainHttpOAuthUri(String target) {
        // given
        URI insecureUri = URI.create("http://api.notion.com/v1/oauth");

        // when
        Throwable thrown = catchThrowable(
                () -> propertiesReplacing(
                        target,
                        insecureUri
                )
        );

        // then
        assertInvalidConfiguration(thrown);
    }

    @DisplayName("user-info가 포함된 OAuth URI 설정은 거부한다")
    @ParameterizedTest
    @ValueSource(strings = {"authorizationUri", "tokenUri", "callbackUri"})
    void create_failure_userInfoOAuthUri(String target) {
        // given
        URI userInfoUri = URI.create("https://client-secret@api.notion.com/v1/oauth");

        // when
        Throwable thrown = catchThrowable(
                () -> propertiesReplacing(
                        target,
                        userInfoUri
                )
        );

        // then
        assertInvalidConfiguration(thrown);
    }

    @DisplayName("host가 없는 OAuth URI 설정은 거부한다")
    @ParameterizedTest
    @ValueSource(strings = {"authorizationUri", "tokenUri", "callbackUri"})
    void create_failure_missingHostOAuthUri(String target) {
        // given
        URI missingHostUri = URI.create("https:/v1/oauth");

        // when
        Throwable thrown = catchThrowable(
                () -> propertiesReplacing(
                        target,
                        missingHostUri
                )
        );

        // then
        assertInvalidConfiguration(thrown);
    }

    @DisplayName("상대 success/failure redirect URI 설정은 허용한다")
    @Test
    void create_success_relativeRedirectUris() {
        // given

        // when
        NotionOAuthProperties properties = validProperties();

        // then
        assertThat(properties.successRedirectUri()).isEqualTo(URI.create("/notion-connected"));
        assertThat(properties.failureRedirectUri()).isEqualTo(URI.create("/notion-failed"));
    }

    private NotionOAuthProperties validProperties() {
        return new NotionOAuthProperties(
                "client-id",
                "client-secret",
                URI.create("https://api.notion.com/v1/oauth/authorize"),
                URI.create("https://api.notion.com/v1/oauth/token"),
                "2026-03-11",
                URI.create("https://api.example.com/api/v1/notion/oauth/callback"),
                URI.create("/notion-connected"),
                URI.create("/notion-failed"),
                Duration.ofMinutes(10),
                Duration.ofSeconds(5),
                "v1",
                STATE_HASH_KEY,
                Map.of(
                        "v1",
                        ENCRYPTION_KEY
                )
        );
    }

    private NotionOAuthProperties propertiesReplacing(
            String target,
            URI uri
    ) {
        URI authorizationUri = URI.create("https://api.notion.com/v1/oauth/authorize");
        URI tokenUri = URI.create("https://api.notion.com/v1/oauth/token");
        URI callbackUri = URI.create("https://api.example.com/api/v1/notion/oauth/callback");
        return properties(
                "authorizationUri".equals(target) ? uri : authorizationUri,
                "tokenUri".equals(target) ? uri : tokenUri,
                "callbackUri".equals(target) ? uri : callbackUri
        );
    }

    private NotionOAuthProperties properties(
            URI authorizationUri,
            URI tokenUri,
            URI callbackUri
    ) {
        return new NotionOAuthProperties(
                "client-id",
                "client-secret",
                authorizationUri,
                tokenUri,
                "2026-03-11",
                callbackUri,
                URI.create("/notion-connected"),
                URI.create("/notion-failed"),
                Duration.ofMinutes(10),
                Duration.ofSeconds(5),
                "v1",
                STATE_HASH_KEY,
                Map.of(
                        "v1",
                        ENCRYPTION_KEY
                )
        );
    }

    private void assertInvalidConfiguration(Throwable thrown) {
        assertThat(thrown).isInstanceOfSatisfying(
                NotionException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(NotionErrorCode.NOTION_OAUTH_CONFIGURATION_INVALID)
        );
    }
}
