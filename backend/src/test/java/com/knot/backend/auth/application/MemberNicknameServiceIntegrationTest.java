package com.knot.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.domain.MemberException;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = AutowireMode.ALL)
class MemberNicknameServiceIntegrationTest {
    private final MemberNicknameService memberNicknameService;
    private final JdbcTemplate jdbcTemplate;

    MemberNicknameServiceIntegrationTest(
            MemberNicknameService memberNicknameService,
            JdbcTemplate jdbcTemplate
    ) {
        this.memberNicknameService = memberNicknameService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.update("TRUNCATE TABLE workspace_members, oauth_identities, members RESTART IDENTITY");
    }

    @Test
    @DisplayName("닉네임 설정을 완료하면 member와 OAuth identity를 함께 저장한다")
    void completeNicknameSetup_success_savesMemberAndIdentity() {
        // given

        // when
        memberNicknameService.completeNicknameSetup(
                oauthUser(),
                "octocat"
        );

        // then
        assertThat(count("members")).isEqualTo(1);
        assertThat(count("oauth_identities")).isEqualTo(1);
    }

    @Test
    @DisplayName("유효하지 않은 닉네임이면 member와 OAuth identity를 저장하지 않는다")
    void completeNicknameSetup_failure_invalidNicknameRollsBack() {
        // given

        // when
        Throwable thrown = catchThrowable(
                () -> memberNicknameService.completeNicknameSetup(
                        oauthUser(),
                        " "
                )
        );

        // then
        assertThat(thrown).isInstanceOf(MemberException.class);
        assertThat(count("members")).isZero();
        assertThat(count("oauth_identities")).isZero();
    }

    @Test
    @DisplayName("같은 OAuth 사용자가 다시 닉네임을 설정하면 중복 예외가 발생한다")
    void completeNicknameSetup_failure_duplicateIdentityRollsBack() {
        // given
        memberNicknameService.completeNicknameSetup(
                oauthUser(),
                "octocat"
        );

        // when
        Throwable thrown = catchThrowable(
                () -> memberNicknameService.completeNicknameSetup(
                        oauthUser(),
                        "other"
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.NICKNAME_SETUP_ALREADY_COMPLETED)
        );
        assertThat(count("members")).isEqualTo(1);
        assertThat(count("oauth_identities")).isEqualTo(1);
    }

    private OAuthUser oauthUser() {
        return OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                "https://example.com/avatar"
        );
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class
        );
    }
}
