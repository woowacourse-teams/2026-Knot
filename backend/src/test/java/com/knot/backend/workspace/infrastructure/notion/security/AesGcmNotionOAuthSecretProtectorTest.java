package com.knot.backend.workspace.infrastructure.notion.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.workspace.application.ContentSourceCredentialKind;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.infrastructure.notion.oauth.NotionOAuthProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmNotionOAuthSecretProtectorTest {
    private static final String STATE_HASH_KEY = "bm90aW9uLXN0YXRlLWhhc2gta2V5LTAwMDAwMDAwMDA";
    private static final String ENCRYPTION_KEY_V1 = "bm90aW9uLWVuY3J5cHRpb24ta2V5LTAwMDAwMDAwMDA";
    private static final String ENCRYPTION_KEY_V2 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY";
    private static final Long WORKSPACE_ID = 1L;
    private static final String SECRET = "secret-notion-token";

    @DisplayName("같은 OAuth state는 항상 같은 비가역 hash로 변환한다")
    @Test
    void hashState_success_deterministic() {
        // given
        AesGcmNotionOAuthSecretProtector protector = protector(
                "v1",
                Map.of(
                        "v1",
                        ENCRYPTION_KEY_V1
                )
        );
        String firstHash = protector.hashState(
                ContentSourceProvider.NOTION,
                "oauth-state"
        );

        // when
        String secondHash = protector.hashState(
                ContentSourceProvider.NOTION,
                "oauth-state"
        );

        // then
        assertThat(secondHash).isEqualTo(firstHash)
                .isNotEqualTo("oauth-state");
    }

    @DisplayName("AES-GCM token envelope에 활성 key version을 기록하고 원문을 복원한다")
    @Test
    void encryptAndDecrypt_success_activeKeyVersion() {
        // given
        AesGcmNotionOAuthSecretProtector protector = protector(
                "v2",
                Map.of(
                        "v1",
                        ENCRYPTION_KEY_V1,
                        "v2",
                        ENCRYPTION_KEY_V2
                )
        );
        String envelope = protector.encrypt(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                SECRET
        );

        // when
        String decrypted = protector.decrypt(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                envelope
        );

        // then
        assertThat(envelope).startsWith("v1:v2:");
        assertThat(decrypted).isEqualTo(SECRET);
    }

    @DisplayName("active key가 바뀌어도 key ring에 남은 이전 key로 token을 복호화한다")
    @Test
    void decrypt_success_previousKeyVersion() {
        // given
        Map<String, String> keyRing = Map.of(
                "v1",
                ENCRYPTION_KEY_V1,
                "v2",
                ENCRYPTION_KEY_V2
        );
        String previousEnvelope = protector(
                "v1",
                keyRing
        ).encrypt(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                ContentSourceCredentialKind.REFRESH_CREDENTIAL,
                SECRET
        );
        AesGcmNotionOAuthSecretProtector rotatedProtector = protector(
                "v2",
                keyRing
        );

        // when
        String decrypted = rotatedProtector.decrypt(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                ContentSourceCredentialKind.REFRESH_CREDENTIAL,
                previousEnvelope
        );

        // then
        assertThat(previousEnvelope).startsWith("v1:v1:");
        assertThat(decrypted).isEqualTo(SECRET);
    }

    @DisplayName("token envelope은 다른 워크스페이스에서 복호화할 수 없다")
    @Test
    void decrypt_failure_differentWorkspace() {
        // given
        AesGcmNotionOAuthSecretProtector protector = protector(
                "v1",
                Map.of(
                        "v1",
                        ENCRYPTION_KEY_V1
                )
        );
        String envelope = protector.encrypt(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                SECRET
        );

        // when
        Throwable thrown = catchThrowable(
                () -> protector.decrypt(
                        2L,
                        ContentSourceProvider.NOTION,
                        ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                        envelope
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                ContentSourceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ContentSourceErrorCode.CONTENT_SOURCE_SECRET_PROTECTION_FAILED)
        );
    }

    @DisplayName("state hash와 token 암호화에 같은 key를 설정하면 시작을 거부한다")
    @Test
    void create_failure_reusedKey() {
        // given
        NotionOAuthProperties properties = properties(
                "v1",
                Map.of(
                        "v1",
                        STATE_HASH_KEY
                )
        );

        // when
        Throwable thrown = catchThrowable(() -> new AesGcmNotionOAuthSecretProtector(properties));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                ContentSourceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ContentSourceErrorCode.CONTENT_SOURCE_CONFIGURATION_INVALID)
        );
    }

    private AesGcmNotionOAuthSecretProtector protector(
            String activeKeyVersion,
            Map<String, String> encryptionKeys
    ) {
        return new AesGcmNotionOAuthSecretProtector(
                properties(
                        activeKeyVersion,
                        encryptionKeys
                )
        );
    }

    private NotionOAuthProperties properties(
            String activeKeyVersion,
            Map<String, String> encryptionKeys
    ) {
        return new NotionOAuthProperties(
                "client-id",
                "client-secret",
                URI.create("https://api.notion.com/v1/oauth/authorize"),
                URI.create("https://api.notion.com/v1/oauth/token"),
                "2026-03-11",
                URI.create("https://api.example.com/api/v1/notion/oauth/callback"),
                URI.create("https://app.example.com/notion-connected"),
                URI.create("https://app.example.com/notion-failed"),
                Duration.ofMinutes(10),
                Duration.ofSeconds(5),
                activeKeyVersion,
                STATE_HASH_KEY,
                encryptionKeys
        );
    }
}
