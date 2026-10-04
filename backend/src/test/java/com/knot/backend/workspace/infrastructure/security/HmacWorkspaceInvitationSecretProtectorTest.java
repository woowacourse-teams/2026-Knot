package com.knot.backend.workspace.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.workspace.application.WorkspaceInvitationSecretKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HmacWorkspaceInvitationSecretProtectorTest {
    private static final String LOOKUP_HASH_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA";
    private static final String SECRET = "secret-value";

    @DisplayName("같은 종류와 원문의 lookup hash는 항상 같다")
    @Test
    void hash_success_deterministic() {
        // given
        HmacWorkspaceInvitationSecretProtector protector = protector();
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
        HmacWorkspaceInvitationSecretProtector protector = protector();
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

    private HmacWorkspaceInvitationSecretProtector protector() {
        return new HmacWorkspaceInvitationSecretProtector(new WorkspaceInvitationSecurityProperties(LOOKUP_HASH_KEY));
    }
}
