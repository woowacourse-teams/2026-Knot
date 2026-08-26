package com.knot.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.infrastructure.jwt.JwtProvider;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
class KnotApplicationTests {
    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final String NICKNAME_COOKIE_NAME = "KNOT_NICKNAME_TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;

    KnotApplicationTests(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
    }

    @Test
    @DisplayName("애플리케이션 컨텍스트가 정상적으로 시작된다")
    void contextLoads() {}

    @Test
    @DisplayName("GitHub OAuth 시작 요청은 GitHub authorization URL로 redirect한다")
    void githubOAuth_start_success() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/oauth2/authorization/github"));

        // then
        result.andExpect(status().isFound())
                .andExpect(
                        header().string(
                                "Location",
                                startsWith("https://github.com/login/oauth/authorize")
                        )
                );
    }

    @Test
    @DisplayName("JWT 쿠키가 있으면 인증된 member 정보를 조회한다")
    void authMe_success() throws Exception {
        // given
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                "octocat",
                "https://example.com/avatar"
        );
        String token = authTokenProvider.issue(member);

        // when
        ResultActions result = mockMvc.perform(
                get("/auth/me").cookie(
                        new Cookie(
                                JWT_COOKIE_NAME,
                                token
                        )
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.nickname").value("octocat"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/avatar"));
    }

    @Test
    @DisplayName("인증되지 않은 member 조회 요청은 Controller에 도달하지 않고 거부된다")
    void authMe_failure_unauthorized() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/auth/me"));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다"));
    }

    @Test
    @DisplayName("잘못된 JWT 쿠키가 있으면 구조화된 401 응답을 반환한다")
    void authMe_failure_invalidToken() throws Exception {
        // when
        ResultActions result = mockMvc.perform(
                get("/auth/me").cookie(
                        new Cookie(
                                JWT_COOKIE_NAME,
                                "invalid-token"
                        )
                )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("만료된 JWT 쿠키가 있으면 인증되지 않은 요청으로 처리한다")
    void authMe_failure_expiredToken() throws Exception {
        // given
        String expiredToken = expiredAccessToken();

        // when
        ResultActions result = mockMvc.perform(
                get("/auth/me").cookie(
                        new Cookie(
                                JWT_COOKIE_NAME,
                                expiredToken
                        )
                )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("닉네임 설정용 JWT를 일반 인증 쿠키로 보내면 인증되지 않는다")
    void authMe_failure_nicknameToken() throws Exception {
        // given
        String nicknameToken = authTokenProvider.issueNickname(
                OAuthUser.of(
                        OAuthProvider.GITHUB,
                        uniqueValue("nickname-user-"),
                        null
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                get("/auth/me").cookie(
                        new Cookie(
                                JWT_COOKIE_NAME,
                                nicknameToken
                        )
                )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("CSRF 쿠키와 헤더가 있으면 닉네임 설정 요청이 실제 보안 필터를 통과한다")
    void completeNicknameSetup_success_withCookieCsrfToken() throws Exception {
        // given
        MvcResult csrfResult = mockMvc.perform(get("/auth/me"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse()
                .getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        String csrfToken = csrfCookie.getValue();
        String nickname = uniqueValue("user-");
        String nicknameToken = authTokenProvider.issueNickname(
                OAuthUser.of(
                        OAuthProvider.GITHUB,
                        uniqueValue("csrf-user-"),
                        null
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                post("/auth/nickname").cookie(
                        new Cookie(
                                NICKNAME_COOKIE_NAME,
                                nicknameToken
                        ),
                        new Cookie(
                                CSRF_COOKIE_NAME,
                                csrfToken
                        )
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}")
        );

        // then
        result.andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("닉네임 설정 요청은 CSRF 토큰이 없으면 거부한다")
    void completeNicknameSetup_failure_missingCsrfToken() throws Exception {
        // given
        String nicknameToken = authTokenProvider.issueNickname(
                OAuthUser.of(
                        OAuthProvider.GITHUB,
                        uniqueValue("missing-csrf-user-"),
                        null
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                post("/auth/nickname").cookie(
                        new Cookie(
                                NICKNAME_COOKIE_NAME,
                                nicknameToken
                        )
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + uniqueValue("user-") + "\"}")
        );

        // then
        result.andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그아웃하면 JWT 쿠키를 만료시킨다")
    void logout_success() throws Exception {
        // given
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                "octocat",
                null
        );
        String token = authTokenProvider.issue(member);
        MvcResult csrfResult = mockMvc.perform(get("/auth/me"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse()
                .getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        String csrfToken = csrfCookie.getValue();

        // when
        ResultActions result = mockMvc.perform(
                post("/logout").cookie(
                        new Cookie(
                                JWT_COOKIE_NAME,
                                token
                        ),
                        new Cookie(
                                NICKNAME_COOKIE_NAME,
                                "nickname-token"
                        )
                )
                        .cookie(csrfCookie)
                        .header(
                                "X-XSRF-TOKEN",
                                csrfToken
                        )
        );

        // then
        result.andExpect(status().isFound())
                .andExpect(resultActions -> {
                    List<String> cookies = resultActions.getResponse()
                            .getHeaders("Set-Cookie");
                    assertThat(cookies).anySatisfy(
                            cookie -> assertThat(cookie).contains(
                                    JWT_COOKIE_NAME + "=",
                                    "Max-Age=0"
                            )
                    );
                    assertThat(cookies).anySatisfy(
                            cookie -> assertThat(cookie).contains(
                                    NICKNAME_COOKIE_NAME + "=",
                                    "Max-Age=0"
                            )
                    );
                });
    }

    private String expiredAccessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-jwt-secret-012345678901234567890123456789");
        properties.setExpiration(Duration.ofHours(1));
        properties.setCookieName(JWT_COOKIE_NAME);
        properties.setSecure(false);
        JwtProvider provider = new JwtProvider(
                properties,
                Clock.fixed(
                        Instant.now()
                                .minus(Duration.ofHours(2)),
                        ZoneOffset.UTC
                )
        );
        return provider.issue(
                AuthenticatedMember.of(
                        1L,
                        "octocat",
                        null
                )
        );
    }

    private String uniqueValue(String prefix) {
        return prefix + UUID.randomUUID()
                .toString()
                .replace(
                        "-",
                        ""
                )
                .substring(
                        0,
                        12
                );
    }
}
