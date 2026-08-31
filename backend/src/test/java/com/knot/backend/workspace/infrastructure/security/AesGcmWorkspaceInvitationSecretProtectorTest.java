package com.knot.backend.workspace.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.workspace.application.WorkspaceInvitationSecretKind;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmWorkspaceInvitationSecretProtectorTest {
    private static final String LOOKUP_HASH_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA";
    private static final String ENCRYPTION_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY";
    private static final Long WORKSPACE_ID = 1L;
    private static final String SECRET = "ABCDEFGH";

    @DisplayName("같은 종류와 원문의 lookup hash는 항상 같다")
    @Test
    void hash_success_deterministic() {
        // given
        AesGcmWorkspaceInvitationSecretProtector protector = protector();
        String firstHash = protector.hash(
                WorkspaceInvitationSecretKind.INVITE_CODE,
                SECRET
        );

        // when
        String secondHash = protector.hash(
                WorkspaceInvitationSecretKind.INVITE_CODE,
                SECRET
        );

        // then
        assertThat(secondHash).isEqualTo(firstHash);
    }

    @DisplayName("같은 원문이라도 링크 토큰과 초대 코드는 서로 다른 lookup hash를 만든다")
    @Test
    void hash_success_separatesSecretKinds() {
        // given
        AesGcmWorkspaceInvitationSecretProtector protector = protector();
        String inviteCodeHash = protector.hash(
                WorkspaceInvitationSecretKind.INVITE_CODE,
                SECRET
        );

        // when
        String linkTokenHash = protector.hash(
                WorkspaceInvitationSecretKind.LINK_TOKEN,
                SECRET
        );

        // then
        assertThat(linkTokenHash).isNotEqualTo(inviteCodeHash);
    }

    @DisplayName("AES-GCM 암호문을 같은 워크스페이스와 종류에서 원문으로 복원한다")
    @Test
    void encryptAndDecrypt_success() {
        // given
        AesGcmWorkspaceInvitationSecretProtector protector = protector();
        String envelope = protector.encrypt(
                WORKSPACE_ID,
                WorkspaceInvitationSecretKind.INVITE_CODE,
                SECRET
        );

        // when
        String decrypted = protector.decrypt(
                WORKSPACE_ID,
                WorkspaceInvitationSecretKind.INVITE_CODE,
                envelope
        );

        // then
        assertThat(decrypted).isEqualTo(SECRET);
    }

    @DisplayName("같은 원문을 두 번 암호화해도 random nonce로 다른 암호문을 만든다")
    @Test
    void encrypt_success_randomNonce() {
        // given
        AesGcmWorkspaceInvitationSecretProtector protector = protector();
        String firstEnvelope = protector.encrypt(
                WORKSPACE_ID,
                WorkspaceInvitationSecretKind.INVITE_CODE,
                SECRET
        );

        // when
        String secondEnvelope = protector.encrypt(
                WORKSPACE_ID,
                WorkspaceInvitationSecretKind.INVITE_CODE,
                SECRET
        );

        // then
        assertThat(secondEnvelope).isNotEqualTo(firstEnvelope);
    }

    @DisplayName("암호문이 변조되면 원문과 암호문을 노출하지 않고 복구를 거부한다")
    @Test
    void decrypt_failure_tamperedEnvelope() {
        // given
        AesGcmWorkspaceInvitationSecretProtector protector = protector();
        String envelope = protector.encrypt(
                WORKSPACE_ID,
                WorkspaceInvitationSecretKind.INVITE_CODE,
                SECRET
        );
        String tamperedEnvelope = envelope.substring(
                0,
                envelope.length() - 1
        ) + (envelope.endsWith("A") ? "B" : "A");

        // when
        Throwable thrown = catchThrowable(
                () -> protector.decrypt(
                        WORKSPACE_ID,
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        tamperedEnvelope
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                WorkspaceException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED);
                    assertThat(exception.getMessage()).doesNotContain(
                            SECRET,
                            envelope,
                            tamperedEnvelope
                    );
                }
        );
    }

    @DisplayName("다른 워크스페이스에서는 같은 암호문을 복호화할 수 없다")
    @Test
    void decrypt_failure_differentWorkspace() {
        // given
        AesGcmWorkspaceInvitationSecretProtector protector = protector();
        String envelope = protector.encrypt(
                WORKSPACE_ID,
                WorkspaceInvitationSecretKind.LINK_TOKEN,
                SECRET
        );

        // when
        Throwable thrown = catchThrowable(
                () -> protector.decrypt(
                        2L,
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        envelope
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                WorkspaceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED)
        );
    }

    private AesGcmWorkspaceInvitationSecretProtector protector() {
        return new AesGcmWorkspaceInvitationSecretProtector(
                new WorkspaceInvitationSecurityProperties(
                        LOOKUP_HASH_KEY,
                        ENCRYPTION_KEY
                )
        );
    }
}
