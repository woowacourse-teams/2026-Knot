package com.knot.backend.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 디바이스 코드·리프레시 토큰처럼 서버가 만들어 주고 해시로만 보관하는 불투명 토큰(기획서 5.2 토큰 규격).
 *
 * 256-bit 난수를 base64url(패딩 없음, 43자)로 낸다. 저장은 SHA-256 16진수(64자)다 — 토큰 자체가 전 엔트로피 난수라 키 있는
 * 해시(HMAC)의 이득이 없고, 새 비밀 설정을 배포에 더하지 않는 편이 되돌리기 쉽다(로드맵 Q57). PKCE `code_verifier`의 S256
 * 검증도 같은 다이제스트를 쓴다(RFC 7636 §4.6).
 */
public final class OpaqueToken {
    public static final int TOKEN_BYTES = 32;
    public static final int HASH_LENGTH = 64;
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final Pattern CODE_VERIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9._~-]{43,128}$");

    private OpaqueToken() {}

    public static String generate(SecureRandom secureRandom) {
        if (secureRandom == null) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /** 저장용 해시. 입력이 비면 조회조차 하지 않도록 예외를 던진다 */
    public static String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR);
        }
        return HexFormat.of()
                .formatHex(digest(token));
    }

    /** RFC 7636 §4.6: `BASE64URL(SHA256(code_verifier)) == code_challenge` */
    public static boolean matchesCodeChallenge(
            String codeVerifier,
            String codeChallenge
    ) {
        if (codeVerifier == null || codeChallenge == null || !CODE_VERIFIER_PATTERN.matcher(codeVerifier)
                .matches()) {
            return false;
        }
        String derived = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(digest(codeVerifier));
        return MessageDigest.isEqual(
                derived.getBytes(StandardCharsets.US_ASCII),
                codeChallenge.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM)
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new AuthException(
                    AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR,
                    exception
            );
        }
    }
}
