package com.knot.backend.workspace.infrastructure.notion.security;

import com.knot.backend.workspace.application.ContentSourceCredentialKind;
import com.knot.backend.workspace.application.ContentSourceSecretProtector;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.infrastructure.notion.oauth.NotionOAuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcmNotionOAuthSecretProtector implements ContentSourceSecretProtector {
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String HASH_ALGORITHM = "HmacSHA256";
    private static final String ENVELOPE_VERSION = "v1";
    private static final String ENVELOPE_DELIMITER = ":";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final int KEY_BYTES = 32;

    private final String activeKeyVersion;
    private final List<VersionedEncryptionKey> encryptionKeys;
    private final SecretKeySpec stateHashKey;
    private final SecureRandom secureRandom;

    public AesGcmNotionOAuthSecretProtector(NotionOAuthProperties properties) {
        this(
                properties,
                new SecureRandom()
        );
    }

    AesGcmNotionOAuthSecretProtector(
            NotionOAuthProperties properties,
            SecureRandom secureRandom
    ) {
        validateProperties(properties);
        this.activeKeyVersion = requireKeyVersion(properties.activeEncryptionKeyVersion());
        this.encryptionKeys = decodeEncryptionKeys(properties.encryptionKeys());
        if (findEncryptionKey(activeKeyVersion) == null) {
            throw configurationInvalid();
        }
        byte[] decodedStateHashKey = decodeConfiguredKey(properties.stateHashKey());
        rejectReusedKey(decodedStateHashKey);
        this.stateHashKey = new SecretKeySpec(
                decodedStateHashKey,
                HASH_ALGORITHM
        );
        if (secureRandom == null) {
            throw configurationInvalid();
        }
        this.secureRandom = secureRandom;
    }

    @Override
    public String hashState(
            ContentSourceProvider provider,
            String state
    ) {
        validateProvider(provider);
        if (state == null || state.isBlank()) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
        try {
            Mac mac = Mac.getInstance(HASH_ALGORITHM);
            mac.init(stateHashKey);
            return encode(
                    mac.doFinal(
                            ("content-source-authorization:" + provider.name() + ":" + state)
                                    .getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (GeneralSecurityException exception) {
            throw protectionFailed(exception);
        }
    }

    @Override
    public String encrypt(
            Long workspaceId,
            ContentSourceProvider provider,
            ContentSourceCredentialKind kind,
            String secret
    ) {
        validateCredentialContext(
                workspaceId,
                provider,
                kind,
                secret
        );
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    findEncryptionKey(activeKeyVersion),
                    new GCMParameterSpec(
                            GCM_TAG_BITS,
                            nonce
                    )
            );
            cipher.updateAAD(
                    context(
                            workspaceId,
                            provider,
                            kind
                    )
            );
            byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return String.join(
                    ENVELOPE_DELIMITER,
                    ENVELOPE_VERSION,
                    activeKeyVersion,
                    encode(nonce),
                    encode(ciphertext)
            );
        } catch (GeneralSecurityException exception) {
            throw protectionFailed(exception);
        }
    }

    @Override
    public String decrypt(
            Long workspaceId,
            ContentSourceProvider provider,
            ContentSourceCredentialKind kind,
            String envelope
    ) {
        if (workspaceId == null || workspaceId <= 0 || provider == null || kind == null || envelope == null
                || envelope.isBlank()) {
            throw protectionFailed();
        }
        String[] parts = envelope.split(
                ENVELOPE_DELIMITER,
                -1
        );
        if (parts.length != 4 || !ENVELOPE_VERSION.equals(parts[0])) {
            throw protectionFailed();
        }
        SecretKeySpec key = findEncryptionKey(parts[1]);
        if (key == null) {
            throw protectionFailed();
        }
        try {
            byte[] nonce = decode(parts[2]);
            byte[] ciphertext = decode(parts[3]);
            if (nonce.length != NONCE_BYTES) {
                throw protectionFailed();
            }
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(
                            GCM_TAG_BITS,
                            nonce
                    )
            );
            cipher.updateAAD(
                    context(
                            workspaceId,
                            provider,
                            kind
                    )
            );
            return new String(
                    cipher.doFinal(ciphertext),
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw protectionFailed(exception);
        }
    }

    private void validateProperties(NotionOAuthProperties properties) {
        if (properties == null) {
            throw configurationInvalid();
        }
    }

    private void validateProvider(ContentSourceProvider provider) {
        if (provider == null) {
            throw new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION);
        }
    }

    private String requireKeyVersion(String keyVersion) {
        if (keyVersion == null || !keyVersion.matches("[A-Za-z0-9._-]{1,32}")) {
            throw configurationInvalid();
        }
        return keyVersion;
    }

    private List<VersionedEncryptionKey> decodeEncryptionKeys(Map<String, String> encodedKeys) {
        if (encodedKeys == null || encodedKeys.isEmpty()) {
            throw configurationInvalid();
        }
        List<VersionedEncryptionKey> decodedKeys = new ArrayList<>();
        encodedKeys.forEach(
                (
                        version,
                        encodedKey
                ) -> decodedKeys.add(
                        new VersionedEncryptionKey(
                                requireKeyVersion(version),
                                new SecretKeySpec(
                                        decodeConfiguredKey(encodedKey),
                                        ENCRYPTION_ALGORITHM
                                )
                        )
                )
        );
        return List.copyOf(decodedKeys);
    }

    private SecretKeySpec findEncryptionKey(String version) {
        return encryptionKeys.stream()
                .filter(
                        key -> key.version()
                                .equals(version)
                )
                .map(VersionedEncryptionKey::secretKey)
                .findFirst()
                .orElse(null);
    }

    private byte[] decodeConfiguredKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw configurationInvalid();
        }
        try {
            byte[] key = decode(encodedKey);
            if (key.length != KEY_BYTES) {
                throw configurationInvalid();
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new ContentSourceException(
                    ContentSourceErrorCode.CONTENT_SOURCE_CONFIGURATION_INVALID,
                    exception
            );
        }
    }

    private void rejectReusedKey(byte[] decodedStateHashKey) {
        boolean reused = encryptionKeys.stream()
                .map(VersionedEncryptionKey::secretKey)
                .map(SecretKeySpec::getEncoded)
                .anyMatch(
                        key -> MessageDigest.isEqual(
                                key,
                                decodedStateHashKey
                        )
                );
        if (reused) {
            throw configurationInvalid();
        }
    }

    private void validateCredentialContext(
            Long workspaceId,
            ContentSourceProvider provider,
            ContentSourceCredentialKind kind,
            String secret
    ) {
        if (workspaceId == null || workspaceId <= 0 || provider == null || kind == null || secret == null
                || secret.isBlank()) {
            throw protectionFailed();
        }
    }

    private byte[] context(
            Long workspaceId,
            ContentSourceProvider provider,
            ContentSourceCredentialKind kind
    ) {
        return (workspaceId + ":" + provider.name() + ":" + kind.context()).getBytes(StandardCharsets.UTF_8);
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder()
                .decode(value);
    }

    private ContentSourceException configurationInvalid() {
        return new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_CONFIGURATION_INVALID);
    }

    private ContentSourceException protectionFailed() {
        return new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_SECRET_PROTECTION_FAILED);
    }

    private ContentSourceException protectionFailed(Throwable cause) {
        return new ContentSourceException(
                ContentSourceErrorCode.CONTENT_SOURCE_SECRET_PROTECTION_FAILED,
                cause
        );
    }

    private record VersionedEncryptionKey(
            String version,
            SecretKeySpec secretKey
    ) {
    }
}
