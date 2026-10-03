package com.knot.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.application.dto.command.CompleteNicknameCommand;
import com.knot.backend.auth.application.dto.result.AuthLoginResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthSession;
import com.knot.backend.auth.domain.AuthSessionRepository;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.domain.RefreshToken;
import com.knot.backend.auth.domain.RefreshTokenProvider;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthNicknameCompletionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String REFRESH_HASH = "a".repeat(64);

    @MockitoBean
    private AuthTokenProvider authTokenProvider;
    @MockitoBean
    private RefreshTokenProvider refreshTokenProvider;
    @MockitoBean
    private Clock clock;

    private final AuthService authService;
    private final AuthSessionRepository sessionRepository;
    private final JdbcTemplate jdbcTemplate;

    AuthNicknameCompletionIntegrationTest(
            AuthService authService,
            AuthSessionRepository sessionRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.authService = authService;
        this.sessionRepository = sessionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.update(
                "TRUNCATE TABLE workspace_members, oauth_identities, auth_sessions, members RESTART IDENTITY CASCADE"
        );
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    @DisplayName("가입 완료 시 member·identity·세션을 저장하고 최초 OAuth 로그인 기준 만료를 적용한다")
    void completeNicknameSetup_success_persistsMemberIdentityAndSession() {
        // given
        Instant firstOAuthLoginAt = NOW.minus(Duration.ofDays(29));
        stubOnboardingToken(firstOAuthLoginAt);
        when(authTokenProvider.issue(any())).thenReturn("access-token");
        when(refreshTokenProvider.issue()).thenReturn(
                RefreshToken.of(
                        "refresh-token",
                        REFRESH_HASH
                )
        );

        // when
        AuthLoginResult result = authService.completeNicknameSetup(
                new CompleteNicknameCommand(
                        "nickname-token",
                        "valid-user"
                )
        );

        // then
        assertThat(result.token()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.refreshMaxAge()).isEqualTo(Duration.ofDays(1));
        assertThat(count("members")).isEqualTo(1);
        assertThat(count("oauth_identities")).isEqualTo(1);
        assertThat(count("auth_sessions")).isEqualTo(1);
        assertThat(
                jdbcTemplate.queryForObject(
                        "SELECT refresh_token_hash FROM auth_sessions",
                        String.class
                )
        ).isEqualTo(REFRESH_HASH);
        assertThat(
                jdbcTemplate.queryForObject(
                        "SELECT absolute_expires_at FROM auth_sessions",
                        OffsetDateTime.class
                )
                        .toInstant()
        ).isEqualTo(firstOAuthLoginAt.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("access token 발급에 실패하면 member·identity·세션을 모두 rollback한다")
    void completeNicknameSetup_failure_accessTokenIssuanceRollsBack() {
        // given
        stubOnboardingToken(NOW);
        when(authTokenProvider.issue(any())).thenThrow(new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR));

        // when & then
        assertThatThrownBy(
                () -> authService.completeNicknameSetup(
                        new CompleteNicknameCommand(
                                "nickname-token",
                                "valid-user"
                        )
                )
        ).isInstanceOf(AuthException.class);
        assertThat(count("members")).isZero();
        assertThat(count("oauth_identities")).isZero();
        assertThat(count("auth_sessions")).isZero();
    }

    @Test
    @DisplayName("refresh token 발급에 실패하면 member와 identity를 모두 rollback한다")
    void completeNicknameSetup_failure_refreshTokenIssuanceRollsBack() {
        // given
        stubOnboardingToken(NOW);
        when(authTokenProvider.issue(any())).thenReturn("access-token");
        when(refreshTokenProvider.issue()).thenThrow(new AuthException(AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR));

        // when & then
        assertThatThrownBy(
                () -> authService.completeNicknameSetup(
                        new CompleteNicknameCommand(
                                "nickname-token",
                                "valid-user"
                        )
                )
        ).isInstanceOf(AuthException.class);
        assertThat(count("members")).isZero();
        assertThat(count("oauth_identities")).isZero();
        assertThat(count("auth_sessions")).isZero();
    }

    @Test
    @DisplayName("세션 저장에 실패하면 새 member와 identity를 rollback한다")
    void completeNicknameSetup_failure_sessionStorageRollsBack() {
        // given
        Long existingMemberId = jdbcTemplate.queryForObject(
                "INSERT INTO members (nickname) VALUES ('existing') RETURNING id",
                Long.class
        );
        sessionRepository.save(
                AuthSession.create(
                        existingMemberId,
                        REFRESH_HASH,
                        NOW.minusSeconds(1)
                )
        );
        stubOnboardingToken(NOW);
        when(authTokenProvider.issue(any())).thenReturn("access-token");
        when(refreshTokenProvider.issue()).thenReturn(
                RefreshToken.of(
                        "duplicate-refresh",
                        REFRESH_HASH
                )
        );

        // when & then
        assertThatThrownBy(
                () -> authService.completeNicknameSetup(
                        new CompleteNicknameCommand(
                                "nickname-token",
                                "valid-user"
                        )
                )
        ).isInstanceOf(AuthException.class);
        assertThat(count("members")).isEqualTo(1);
        assertThat(count("oauth_identities")).isZero();
        assertThat(count("auth_sessions")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 OAuth 계정의 동시 가입 완료는 하나만 저장하고 나머지는 충돌로 응답한다")
    void completeNicknameSetup_failure_concurrentDuplicate() throws Exception {
        // given
        stubOnboardingToken(NOW);
        when(authTokenProvider.issue(any())).thenReturn("access-token");
        when(refreshTokenProvider.issue()).thenReturn(
                RefreshToken.of(
                        "first-refresh",
                        "a".repeat(64)
                ),
                RefreshToken.of(
                        "second-refresh",
                        "b".repeat(64)
                )
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when
        try {
            Future<CompletionOutcome> first = executor.submit(
                    () -> completeAtSameTime(
                            ready,
                            start,
                            "first-name"
                    )
            );
            Future<CompletionOutcome> second = executor.submit(
                    () -> completeAtSameTime(
                            ready,
                            start,
                            "second-name"
                    )
            );
            assertThat(
                    ready.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
            start.countDown();
            List<CompletionOutcome> outcomes = List.of(
                    first.get(
                            10,
                            TimeUnit.SECONDS
                    ),
                    second.get(
                            10,
                            TimeUnit.SECONDS
                    )
            );

            // then
            assertThat(outcomes).filteredOn(outcome -> outcome.result() != null)
                    .hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> outcome.errorCode() != null)
                    .extracting(CompletionOutcome::errorCode)
                    .containsExactly(AuthErrorCode.NICKNAME_SETUP_ALREADY_COMPLETED.getCode());
            assertThat(count("members")).isEqualTo(1);
            assertThat(count("oauth_identities")).isEqualTo(1);
            assertThat(count("auth_sessions")).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private CompletionOutcome completeAtSameTime(
            CountDownLatch ready,
            CountDownLatch start,
            String nickname
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(
                5,
                TimeUnit.SECONDS
        )) {
            throw new IllegalStateException("동시 가입 테스트의 시작 신호를 받지 못했습니다");
        }
        try {
            return new CompletionOutcome(
                    authService.completeNicknameSetup(
                            new CompleteNicknameCommand(
                                    "nickname-token",
                                    nickname
                            )
                    ),
                    null
            );
        } catch (AuthException exception) {
            return new CompletionOutcome(
                    null,
                    exception.getErrorCode()
                            .getCode()
            );
        }
    }

    private void stubOnboardingToken(Instant authenticatedAt) {
        when(authTokenProvider.authenticateNickname("nickname-token")).thenReturn(
                OAuthUser.of(
                        OAuthProvider.GITHUB,
                        "42",
                        "https://example.com/avatar",
                        authenticatedAt
                )
        );
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class
        );
    }

    private record CompletionOutcome(
            AuthLoginResult result,
            String errorCode
    ) {
    }
}
