package com.knot.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.RefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenIssuerTest {
    @Test
    @DisplayName("refresh 토큰은 매번 새로운 256비트 난수와 저장용 해시를 반환한다")
    void issue_success() throws Exception {
        // given
        RefreshTokenIssuer issuer = new RefreshTokenIssuer();

        // when
        RefreshToken first = issuer.issue();
        RefreshToken second = issuer.issue();

        // then
        assertThat(first.getValue()).matches("[A-Za-z0-9_-]{43}")
                .isNotEqualTo(second.getValue());
        String expectedHash = HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(
                                        first.getValue()
                                                .getBytes(StandardCharsets.UTF_8)
                                )
                );
        assertThat(first.getHash()).isEqualTo(expectedHash)
                .isNotEqualTo(first.getValue());
        assertThat(first.toString()).doesNotContain(first.getValue());
    }

    @Test
    @DisplayName("값이나 해시가 없는 refresh 토큰은 생성할 수 없다")
    void create_failure_missingCredential() {
        // when & then
        assertThatThrownBy(
                () -> RefreshToken.of(
                        null,
                        "a".repeat(64)
                )
        ).isInstanceOf(AuthException.class);
        assertThatThrownBy(
                () -> RefreshToken.of(
                        " ",
                        "a".repeat(64)
                )
        ).isInstanceOf(AuthException.class);
        assertThatThrownBy(
                () -> RefreshToken.of(
                        "token",
                        " "
                )
        ).isInstanceOf(AuthException.class);
    }
}
