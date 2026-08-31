package com.knot.backend.workspace.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationSecrets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureWorkspaceInvitationSecretGeneratorTest {

    @DisplayName("수동 입력용 코드와 URL-safe 링크 토큰을 생성한다")
    @Test
    void generate_success_format() {
        // given
        SecureWorkspaceInvitationSecretGenerator generator = new SecureWorkspaceInvitationSecretGenerator();

        // when
        WorkspaceInvitationSecrets secrets = generator.generate();

        // then
        assertThat(secrets.code()).matches("[A-HJ-NP-Z2-9]{6}");
        assertThat(secrets.linkToken()).hasSize(43)
                .matches("[A-Za-z0-9_-]+");
    }

    @DisplayName("연속 발급한 코드와 링크 토큰은 서로 다르다")
    @Test
    void generate_success_uniqueValues() {
        // given
        SecureWorkspaceInvitationSecretGenerator generator = new SecureWorkspaceInvitationSecretGenerator();
        WorkspaceInvitationSecrets first = generator.generate();

        // when
        WorkspaceInvitationSecrets second = generator.generate();

        // then
        assertThat(second.code()).isNotEqualTo(first.code());
        assertThat(second.linkToken()).isNotEqualTo(first.linkToken());
    }
}
