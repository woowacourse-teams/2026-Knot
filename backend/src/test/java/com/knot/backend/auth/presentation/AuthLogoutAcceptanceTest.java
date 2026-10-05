package com.knot.backend.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthSession;
import com.knot.backend.auth.domain.AuthSessionRepository;
import com.knot.backend.auth.domain.RefreshToken;
import com.knot.backend.auth.domain.RefreshTokenProvider;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.Member;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
class AuthLogoutAcceptanceTest {
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    private final MockMvc mockMvc;
    private final MemberService memberService;
    private final AuthSessionRepository sessionRepository;
    private final RefreshTokenProvider refreshTokenProvider;
    private final JdbcTemplate jdbcTemplate;

    AuthLogoutAcceptanceTest(
            MockMvc mockMvc,
            MemberService memberService,
            AuthSessionRepository sessionRepository,
            RefreshTokenProvider refreshTokenProvider,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.memberService = memberService;
        this.sessionRepository = sessionRepository;
        this.refreshTokenProvider = refreshTokenProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("refresh token으로 현재 기기만 로그아웃하고 모든 인증 쿠키를 만료한다")
    void logout_success_revokesCurrentSessionAndExpiresCookies() throws Exception {
        // given
        RefreshToken currentRefreshToken = createSession(Instant.now());
        RefreshToken anotherDeviceRefreshToken = createSession(Instant.now());
        Cookie csrfCookie = csrfCookie();

        // when
        MvcResult result = mockMvc.perform(
                post(LOGOUT_PATH).cookie(
                        new Cookie(
                                "KNOT_REFRESH_TOKEN",
                                currentRefreshToken.getValue()
                        ),
                        new Cookie(
                                "KNOT_ACCESS_TOKEN",
                                "invalid-access-token"
                        ),
                        csrfCookie
                )
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
        )
                .andExpect(status().isNoContent())
                .andExpect(content().string(emptyString()))
                .andReturn();

        // then
        assertThat(isRevoked(currentRefreshToken)).isTrue();
        assertThat(isRevoked(anotherDeviceRefreshToken)).isFalse();
        assertThatExpiredLoginCookies(result);

        Cookie repeatCsrfCookie = csrfCookie();
        mockMvc.perform(
                post(LOGOUT_PATH).cookie(
                        new Cookie(
                                "KNOT_REFRESH_TOKEN",
                                currentRefreshToken.getValue()
                        ),
                        repeatCsrfCookie
                )
                        .header(
                                CSRF_HEADER_NAME,
                                repeatCsrfCookie.getValue()
                        )
        )
                .andExpect(status().isNoContent());
        assertThat(isRevoked(currentRefreshToken)).isTrue();
    }

    @Test
    @DisplayName("refresh token이 없어도 204와 인증 쿠키 만료를 반환한다")
    void logout_success_missingRefreshToken() throws Exception {
        // given
        Cookie csrfCookie = csrfCookie();

        // when
        MvcResult result = mockMvc.perform(
                post(LOGOUT_PATH).cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
        )
                .andExpect(status().isNoContent())
                .andExpect(content().string(emptyString()))
                .andReturn();

        // then
        assertThatExpiredLoginCookies(result);
    }

    @Test
    @DisplayName("CSRF 검증에 실패하면 세션을 유지하고 CSRF 오류를 반환한다")
    void logout_failure_invalidCsrf() throws Exception {
        // given
        RefreshToken refreshToken = createSession(Instant.now());
        Cookie csrfCookie = csrfCookie();

        // when
        mockMvc.perform(
                post(LOGOUT_PATH).cookie(
                        new Cookie(
                                "KNOT_REFRESH_TOKEN",
                                refreshToken.getValue()
                        ),
                        csrfCookie
                )
                        .header(
                                CSRF_HEADER_NAME,
                                "invalid-token"
                        )
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        // then
        assertThat(isRevoked(refreshToken)).isFalse();
    }

    @Test
    @DisplayName("만료된 세션이어도 로그아웃 요청은 204로 처리하고 쿠키를 만료한다")
    void logout_success_expiredSession() throws Exception {
        // given
        RefreshToken expiredRefreshToken = createSession(
                Instant.now()
                        .minus(Duration.ofDays(8))
        );
        Cookie csrfCookie = csrfCookie();

        // when
        MvcResult result = mockMvc.perform(
                post(LOGOUT_PATH).cookie(
                        new Cookie(
                                "KNOT_REFRESH_TOKEN",
                                expiredRefreshToken.getValue()
                        ),
                        csrfCookie
                )
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
        )
                .andExpect(status().isNoContent())
                .andExpect(content().string(emptyString()))
                .andReturn();

        // then
        assertThat(isRevoked(expiredRefreshToken)).isFalse();
        assertThatExpiredLoginCookies(result);
    }

    private RefreshToken createSession(Instant createdAt) {
        Member member = memberService.create(
                "lo" + UUID.randomUUID()
                        .toString()
                        .replaceAll(
                                "[0-9-]",
                                "a"
                        )
                        .substring(
                                0,
                                14
                        ),
                null
        );
        RefreshToken refreshToken = refreshTokenProvider.issue();
        sessionRepository.save(
                AuthSession.create(
                        member.getId(),
                        refreshToken.getHash(),
                        createdAt
                )
        );
        return refreshToken;
    }

    private Cookie csrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);
    }

    private boolean isRevoked(RefreshToken refreshToken) {
        return jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM auth_sessions WHERE refresh_token_hash = ?",
                Boolean.class,
                refreshToken.getHash()
        );
    }

    private void assertThatExpiredLoginCookies(MvcResult result) {
        assertThat(
                result.getResponse()
                        .getCookie("KNOT_ACCESS_TOKEN")
                        .getMaxAge()
        ).isZero();
        assertThat(
                result.getResponse()
                        .getCookie("KNOT_REFRESH_TOKEN")
                        .getMaxAge()
        ).isZero();
        assertThat(
                result.getResponse()
                        .getCookie("KNOT_NICKNAME_TOKEN")
                        .getMaxAge()
        ).isZero();
    }
}
