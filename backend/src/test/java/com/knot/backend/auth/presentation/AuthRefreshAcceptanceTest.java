package com.knot.backend.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.application.AuthRefreshService;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthSession;
import com.knot.backend.auth.domain.AuthSessionRepository;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.RefreshToken;
import com.knot.backend.auth.domain.RefreshTokenProvider;
import com.knot.backend.auth.infrastructure.jwt.JwtProvider;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthRefreshAcceptanceTest {
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String REFRESH_COOKIE_NAME = "KNOT_REFRESH_TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    private final MockMvc mockMvc;
    private final AuthRefreshService authRefreshService;
    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenProvider refreshTokenProvider;
    private final JwtProperties jwtProperties;
    private final MemberService memberService;
    private final JdbcTemplate jdbcTemplate;

    AuthRefreshAcceptanceTest(
            MockMvc mockMvc,
            AuthRefreshService authRefreshService,
            AuthSessionRepository authSessionRepository,
            RefreshTokenProvider refreshTokenProvider,
            JwtProperties jwtProperties,
            MemberService memberService,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.authRefreshService = authRefreshService;
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenProvider = refreshTokenProvider;
        this.jwtProperties = jwtProperties;
        this.memberService = memberService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("유효한 refresh 쿠키로 access·refresh 쿠키를 갱신한다")
    void refresh_success_rotatesCookies() throws Exception {
        // given
        RefreshToken currentToken = createActiveRefreshToken();
        long authSessionId = sessionId(currentToken.getHash());
        Cookie csrfCookie = csrfCookie();
        String expiredAccessToken = expiredAccessToken();

        // when
        MvcResult result = mockMvc.perform(
                post(REFRESH_PATH).cookie(
                        new Cookie(
                                "KNOT_ACCESS_TOKEN",
                                expiredAccessToken
                        ),
                        new Cookie(
                                REFRESH_COOKIE_NAME,
                                currentToken.getValue()
                        ),
                        csrfCookie
                )
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
        )
                .andExpect(status().isNoContent())
                .andReturn();

        // then
        assertThat(
                result.getResponse()
                        .getContentAsString()
        ).isEmpty();
        Cookie accessCookie = result.getResponse()
                .getCookie("KNOT_ACCESS_TOKEN");
        Cookie refreshCookie = result.getResponse()
                .getCookie(REFRESH_COOKIE_NAME);
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.getMaxAge()).isEqualTo(3600);
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isNotEqualTo(currentToken.getValue());
        assertThat(refreshCookie.getMaxAge()).isPositive()
                .isLessThanOrEqualTo(7 * 86400);
        assertThat(
                result.getResponse()
                        .getHeaders(HttpHeaders.SET_COOKIE)
        ).hasSize(2);
        assertThat(sessionRefreshTokenHash(authSessionId)).isEqualTo(
                refreshTokenProvider.identify(refreshCookie.getValue())
                        .getHash()
        );
        assertThat(consumedTokenCount(currentToken.getHash())).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh 쿠키가 없으면 401을 반환하고 인증 쿠키를 발급하지 않는다")
    void refresh_failure_missingRefreshCookie() throws Exception {
        // given
        Cookie csrfCookie = csrfCookie();

        // when
        MvcResult result = mockMvc.perform(
                post(REFRESH_PATH).cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn();

        // then
        assertThat(
                result.getResponse()
                        .getHeaders(HttpHeaders.SET_COOKIE)
        ).isEmpty();
    }

    @Test
    @DisplayName("서명되지 않은 refresh 쿠키는 401로 거부한다")
    void refresh_failure_invalidRefreshCookie() throws Exception {
        // given
        Cookie csrfCookie = csrfCookie();

        // when
        MvcResult result = mockMvc.perform(
                post(REFRESH_PATH).cookie(
                        new Cookie(
                                REFRESH_COOKIE_NAME,
                                "not-a-signed-token"
                        ),
                        csrfCookie
                )
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn();

        // then
        assertThat(
                result.getResponse()
                        .getHeaders(HttpHeaders.SET_COOKIE)
        ).isEmpty();
    }

    @Test
    @DisplayName("CSRF 검증에 실패하면 세션을 변경하지 않고 403을 반환한다")
    void refresh_failure_invalidCsrf() throws Exception {
        // given
        RefreshToken currentToken = createActiveRefreshToken();
        long authSessionId = sessionId(currentToken.getHash());
        Cookie csrfCookie = csrfCookie();

        // when
        MvcResult result = mockMvc.perform(
                post(REFRESH_PATH).cookie(
                        new Cookie(
                                REFRESH_COOKIE_NAME,
                                currentToken.getValue()
                        ),
                        csrfCookie
                )
                        .header(
                                CSRF_HEADER_NAME,
                                "invalid-token"
                        )
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andReturn();

        // then
        assertThat(
                result.getResponse()
                        .getHeaders(HttpHeaders.SET_COOKIE)
        ).isEmpty();
        assertThat(sessionRefreshTokenHash(authSessionId)).isEqualTo(currentToken.getHash());
        assertThat(consumedTokenCount(currentToken.getHash())).isZero();
    }

    @Test
    @DisplayName("동시에 같은 refresh를 사용하면 한 번만 회전하고 실패 응답 뒤 세션 폐기를 보존한다")
    void refresh_failure_concurrentReplayRevokesSessionAfterUnauthorized() throws Exception {
        // given
        RefreshToken currentToken = createActiveRefreshToken();
        long sessionId = sessionId(currentToken.getHash());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when
        try {
            Future<Boolean> firstRequest = executor.submit(
                    () -> refreshAfterStartSignal(
                            currentToken.getValue(),
                            ready,
                            start
                    )
            );
            Future<Boolean> secondRequest = executor.submit(
                    () -> refreshAfterStartSignal(
                            currentToken.getValue(),
                            ready,
                            start
                    )
            );
            assertThat(
                    ready.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
            start.countDown();

            boolean firstSucceeded = firstRequest.get(
                    10,
                    TimeUnit.SECONDS
            );
            boolean secondSucceeded = secondRequest.get(
                    10,
                    TimeUnit.SECONDS
            );

            // then
            assertThat(firstSucceeded ^ secondSucceeded).isTrue();
            assertThat(isSessionRevoked(sessionId)).isTrue();
            assertThat(consumedTokenCount(currentToken.getHash())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean refreshAfterStartSignal(
            String refreshToken,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(
                5,
                TimeUnit.SECONDS
        )) {
            throw new IllegalStateException("Refresh requests did not start together");
        }
        try {
            authRefreshService.refresh(refreshToken);
            return true;
        } catch (AuthException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.UNAUTHENTICATED);
            return false;
        }
    }

    private RefreshToken createActiveRefreshToken() {
        Instant now = Instant.now();
        Member member = memberService.create(
                randomNickname(),
                null
        );
        Instant expiresAt = AuthSession.initialRefreshExpirationAt(
                now,
                now
        );
        RefreshToken refreshToken = refreshTokenProvider.issue(expiresAt);
        authSessionRepository.save(
                AuthSession.create(
                        member.getId(),
                        refreshToken.getHash(),
                        now
                )
        );
        return refreshToken;
    }

    private String randomNickname() {
        String candidates = (UUID.randomUUID()
                .toString()
                + UUID.randomUUID()
                        .toString())
                .replaceAll(
                        "[^A-Za-z]",
                        ""
                );
        return "refresh" + candidates.substring(
                0,
                12
        );
    }

    private String expiredAccessToken() {
        Clock expiredClock = Clock.fixed(
                Instant.now()
                        .minusSeconds(7200),
                ZoneOffset.UTC
        );
        JwtProvider expiredTokenIssuer = new JwtProvider(
                jwtProperties,
                expiredClock
        );
        return expiredTokenIssuer.issue(
                AuthenticatedMember.of(
                        1L,
                        "expired-user",
                        null
                )
        );
    }

    private Cookie csrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);
    }

    private long sessionId(String refreshTokenHash) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM auth_sessions WHERE refresh_token_hash = ?",
                Long.class,
                refreshTokenHash
        );
    }

    private String sessionRefreshTokenHash(long authSessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT refresh_token_hash FROM auth_sessions WHERE id = ?",
                String.class,
                authSessionId
        );
    }

    private int consumedTokenCount(String refreshTokenHash) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_session_refresh_token_history WHERE refresh_token_hash = ?",
                Integer.class,
                refreshTokenHash
        );
    }

    private boolean isSessionRevoked(long authSessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM auth_sessions WHERE id = ?",
                Boolean.class,
                authSessionId
        );
    }
}
