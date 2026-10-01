package com.knot.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.domain.RefreshToken;
import com.knot.backend.auth.domain.RefreshTokenProvider;
import com.knot.backend.auth.infrastructure.AuthSessionRepositoryImpl;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({AuthService.class, AuthSessionRepositoryImpl.class, TestcontainersConfiguration.class})
@TestApplicationProperties
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthServiceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @MockitoBean
    private MemberService memberService;
    @MockitoBean
    private OAuthIdentityService identities;
    @MockitoBean
    private MemberNicknameService nicknameService;
    @MockitoBean
    private AuthTokenProvider tokens;
    @MockitoBean
    private RefreshTokenProvider refreshTokens;
    @MockitoBean
    private Clock clock;
    private final AuthService service;
    private final JdbcTemplate jdbc;

    AuthServiceIntegrationTest(
            AuthService service,
            JdbcTemplate jdbc
    ) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clearTables() {
        jdbc.update("TRUNCATE TABLE members RESTART IDENTITY CASCADE");
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    @DisplayName("기존 회원 로그인은 refresh 원문 대신 해시와 세션 만료 시각을 저장한다")
    void login_success_persistsSession() {
        // given
        registerMember();
        when(tokens.issue(any())).thenReturn("access-token");
        when(refreshTokens.issue()).thenReturn(
                RefreshToken.of(
                        "private-refresh",
                        "a".repeat(64)
                )
        );

        // when
        service.login(oauthUser());

        // then
        assertThat(count("auth_sessions")).isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                        "SELECT refresh_token_hash FROM auth_sessions",
                        String.class
                )
        ).isEqualTo("a".repeat(64))
                .isNotEqualTo("private-refresh");
    }

    @Test
    @DisplayName("신규 OAuth 로그인은 회원·identity·인증 세션을 저장하지 않는다")
    void login_success_newUserWithoutPersistence() {
        // given
        when(
                identities.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB,
                        "42"
                )
        ).thenReturn(Optional.empty());
        when(tokens.issueNickname(any())).thenReturn("nickname-token");

        // when
        service.login(oauthUser());

        // then
        assertThat(count("members")).isZero();
        assertThat(count("oauth_identities")).isZero();
        assertThat(count("auth_sessions")).isZero();
        verifyNoInteractions(
                memberService,
                nicknameService,
                refreshTokens
        );
    }

    @Test
    @DisplayName("세션 insert 이후 결과 검증이 실패해도 login 트랜잭션은 rollback된다")
    void login_failure_rollsBackInsertedSession() {
        // given
        registerMember();
        when(tokens.issue(any())).thenReturn(" ");
        when(refreshTokens.issue()).thenReturn(
                RefreshToken.of(
                        "refresh",
                        "a".repeat(64)
                )
        );

        // when & then
        assertThatThrownBy(() -> service.login(oauthUser())).isInstanceOf(AuthException.class);
        assertThat(count("auth_sessions")).isZero();
        assertThat(count("members")).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh 해시 충돌은 기존 세션을 보존하고 새 login만 실패시킨다")
    void login_failure_duplicateHashRollsBack() {
        // given
        registerMember();
        when(tokens.issue(any())).thenReturn("access");
        when(refreshTokens.issue()).thenReturn(
                RefreshToken.of(
                        "refresh",
                        "a".repeat(64)
                )
        );
        service.login(oauthUser());

        // when & then
        assertThatThrownBy(() -> service.login(oauthUser())).isInstanceOf(AuthException.class);
        assertThat(count("auth_sessions")).isEqualTo(1);
    }

    private void registerMember() {
        Long memberId = jdbc.queryForObject(
                "INSERT INTO members (nickname) VALUES ('흑곰') RETURNING id",
                Long.class
        );
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);
        when(member.getNickname()).thenReturn("흑곰");
        when(memberService.findById(memberId)).thenReturn(Optional.of(member));
        when(
                identities.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB,
                        "42"
                )
        ).thenReturn(
                Optional.of(
                        OAuthIdentity.create(
                                oauthUser(),
                                memberId
                        )
                )
        );
    }

    private OAuthUser oauthUser() {
        return OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );
    }

    private int count(String tableName) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class
        );
    }
}
