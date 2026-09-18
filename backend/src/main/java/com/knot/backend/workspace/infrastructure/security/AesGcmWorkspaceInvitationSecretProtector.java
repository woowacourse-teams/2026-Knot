package com.knot.backend.workspace.infrastructure.security;

import com.knot.backend.workspace.application.WorkspaceInvitationSecretKind;
import com.knot.backend.workspace.application.WorkspaceInvitationSecretProtector;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcmWorkspaceInvitationSecretProtector implements WorkspaceInvitationSecretProtector {
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String HASH_ALGORITHM = "HmacSHA256";
    private static final String ENVELOPE_VERSION = "v1";
    private static final String ENVELOPE_DELIMITER = ":";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec lookupHashKey;
    private final SecureRandom secureRandom;

    public AesGcmWorkspaceInvitationSecretProtector(WorkspaceInvitationSecurityProperties properties) {
        this(
                properties,
                new SecureRandom()
        );
    }

    AesGcmWorkspaceInvitationSecretProtector(
            WorkspaceInvitationSecurityProperties properties,
            SecureRandom secureRandom
    ) {
        byte[] decodedEncryptionKey = decodeConfiguredKey(properties == null ? null : properties.encryptionKey());
        byte[] decodedLookupHashKey = decodeConfiguredKey(properties == null ? null : properties.lookupHashKey());
        if (MessageDigest.isEqual(
                decodedEncryptionKey,
                decodedLookupHashKey
        )) {
            throw configurationInvalid();
        }
        this.encryptionKey = new SecretKeySpec(
                decodedEncryptionKey,
                ENCRYPTION_ALGORITHM
        );
        this.lookupHashKey = new SecretKeySpec(
                decodedLookupHashKey,
                HASH_ALGORITHM
        );
        this.secureRandom = requireSecureRandom(secureRandom);
    }

    @Override
    public String hash(
            WorkspaceInvitationSecretKind kind,
            String secret
    ) {
        validateSecret(
                kind,
                secret
        );
        try {
            Mac mac = Mac.getInstance(HASH_ALGORITHM);
            mac.init(lookupHashKey);
            return encode(
                    mac.doFinal(
                            contextualSecret(
                                    kind,
                                    secret
                            )
                    )
            );
        } catch (GeneralSecurityException exception) {
            throw recoveryFailed(exception);
        }
    }

    @Override
    public boolean matches(
            WorkspaceInvitationSecretKind kind,
            String secret,
            String expectedHash
    ) {
        if (expectedHash == null || expectedHash.isBlank()) {
            throw recoveryFailed();
        }
        return MessageDigest.isEqual(
                hash(
                        kind,
                        secret
                ).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    @Override
    public String encrypt(
            Long workspaceId,
            WorkspaceInvitationSecretKind kind,
            String secret
    ) {
        validateEncryptionContext(
                workspaceId,
                kind,
                secret
        );
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(
                            GCM_TAG_BITS,
                            nonce
                    )
            );
            cipher.updateAAD(
                    contextualWorkspace(
                            workspaceId,
                            kind
                    )
            );
            byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return String.join(
                    ENVELOPE_DELIMITER,
                    ENVELOPE_VERSION,
                    encode(nonce),
                    encode(ciphertext)
            );
        } catch (GeneralSecurityException exception) {
            throw recoveryFailed(exception);
        }
    }

    @Override
    public String decrypt(
            Long workspaceId,
            WorkspaceInvitationSecretKind kind,
            String envelope
    ) {
        validateEnvelopeContext(
                workspaceId,
                kind,
                envelope
        );
        String[] parts = envelope.split(
                ENVELOPE_DELIMITER,
                -1
        );
        if (parts.length != 3 || !ENVELOPE_VERSION.equals(parts[0])) {
            throw recoveryFailed();
        }
        try {
            byte[] nonce = decode(parts[1]);
            byte[] ciphertext = decode(parts[2]);
            if (nonce.length != NONCE_BYTES) {
                throw recoveryFailed();
            }
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(
                            GCM_TAG_BITS,
                            nonce
                    )
            );
            cipher.updateAAD(
                    contextualWorkspace(
                            workspaceId,
                            kind
                    )
            );
            return new String(
                    cipher.doFinal(ciphertext),
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw recoveryFailed(exception);
        }
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
            throw new WorkspaceException(
                    WorkspaceErrorCode.WORKSPACE_INVITATION_SECURITY_CONFIGURATION_INVALID,
                    exception
            );
        }
    }

    private SecureRandom requireSecureRandom(SecureRandom secureRandom) {
        if (secureRandom == null) {
            throw configurationInvalid();
        }
        return secureRandom;
    }

    private void validateSecret(
            WorkspaceInvitationSecretKind kind,
            String secret
    ) {
        if (kind == null || secret == null || secret.isBlank()) {
            throw recoveryFailed();
        }
    }

    private void validateEncryptionContext(
            Long workspaceId,
            WorkspaceInvitationSecretKind kind,
            String secret
    ) {
        validateWorkspaceId(workspaceId);
        validateSecret(
                kind,
                secret
        );
    }

    private void validateEnvelopeContext(
            Long workspaceId,
            WorkspaceInvitationSecretKind kind,
            String envelope
    ) {
        validateWorkspaceId(workspaceId);
        if (kind == null || envelope == null || envelope.isBlank()) {
            throw recoveryFailed();
        }
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw recoveryFailed();
        }
    }

    private byte[] contextualSecret(
            WorkspaceInvitationSecretKind kind,
            String secret
    ) {
        return (kind.context() + ":" + secret).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] contextualWorkspace(
            Long workspaceId,
            WorkspaceInvitationSecretKind kind
    ) {
        return (workspaceId + ":" + kind.context()).getBytes(StandardCharsets.UTF_8);
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder()
                .decode(value);
    }

    private WorkspaceException configurationInvalid() {
        return new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_SECURITY_CONFIGURATION_INVALID);
    }

    private WorkspaceException recoveryFailed() {
        return new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED);
    }

    private WorkspaceException recoveryFailed(Throwable cause) {
        return new WorkspaceException(
                WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED,
                cause
        );
    }
}
