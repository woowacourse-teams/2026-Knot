package com.knot.backend.workspace.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.workspace.application.WorkspaceInvitationSecretGenerator;
import com.knot.backend.workspace.application.WorkspaceInvitationSecretProtector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WorkspaceInvitationSecurityConfigTest {
    private static final String LOOKUP_HASH_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA";
    private static final String ENCRYPTION_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WorkspaceInvitationSecurityConfig.class);

    @DisplayName("서로 다른 32-byte key가 있으면 초대 보안 Bean을 생성한다")
    @Test
    void context_success_validKeys() {
        // given
        ApplicationContextRunner configuredRunner = contextRunner.withPropertyValues(
                "workspace.invitation.lookup-hash-key=" + LOOKUP_HASH_KEY,
                "workspace.invitation.encryption-key=" + ENCRYPTION_KEY
        );

        // when
        configuredRunner.run(context -> {
            // then
            assertThat(context).hasSingleBean(WorkspaceInvitationSecretGenerator.class)
                    .hasSingleBean(WorkspaceInvitationSecretProtector.class);
        });
    }

    @DisplayName("초대 보안 key가 없으면 ApplicationContext 시작을 실패한다")
    @Test
    void context_failure_missingKeys() {
        // given

        // when
        contextRunner.run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("워크스페이스 초대 보안 설정이 올바르지 않습니다");
        });
    }

    @DisplayName("초대 보안 key가 Base64 URL 형식이 아니면 ApplicationContext 시작을 실패한다")
    @Test
    void context_failure_invalidKeyFormat() {
        // given
        ApplicationContextRunner configuredRunner = contextRunner.withPropertyValues(
                "workspace.invitation.lookup-hash-key=not base64",
                "workspace.invitation.encryption-key=" + ENCRYPTION_KEY
        );

        // when
        configuredRunner.run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("워크스페이스 초대 보안 설정이 올바르지 않습니다");
        });
    }

    @DisplayName("초대 보안 key가 32 byte가 아니면 ApplicationContext 시작을 실패한다")
    @Test
    void context_failure_invalidKeyLength() {
        // given
        ApplicationContextRunner configuredRunner = contextRunner.withPropertyValues(
                "workspace.invitation.lookup-hash-key=c2hvcnQ",
                "workspace.invitation.encryption-key=" + ENCRYPTION_KEY
        );

        // when
        configuredRunner.run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("워크스페이스 초대 보안 설정이 올바르지 않습니다");
        });
    }

    @DisplayName("lookup hash key와 encryption key가 같으면 ApplicationContext 시작을 실패한다")
    @Test
    void context_failure_sameKeys() {
        // given
        ApplicationContextRunner configuredRunner = contextRunner.withPropertyValues(
                "workspace.invitation.lookup-hash-key=" + ENCRYPTION_KEY,
                "workspace.invitation.encryption-key=" + ENCRYPTION_KEY
        );

        // when
        configuredRunner.run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("워크스페이스 초대 보안 설정이 올바르지 않습니다");
        });
    }
}
