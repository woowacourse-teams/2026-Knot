package com.knot.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
class KnotApplicationTests {
    private static final String FRONTEND_ORIGIN = "https://knoted.kr";
    private static final String UNALLOWED_ORIGIN = "https://attacker.example";
    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final String NICKNAME_COOKIE_NAME = "KNOT_NICKNAME_TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final ObjectMapper objectMapper;

    KnotApplicationTests(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            ObjectMapper objectMapper
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("애플리케이션 컨텍스트가 정상적으로 시작된다")
    void contextLoads_success() {
        // given

        // when

        // then
    }

    @Test
    @DisplayName("GitHub OAuth 시작 요청은 GitHub authorization URL로 redirect한다")
    void githubOAuthStart_success() throws Exception {
        // given

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
                get("/api/v1/auth/me").cookie(
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
        // given

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/auth/me"));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다"));
    }

    @Test
    @DisplayName("잘못된 JWT 쿠키가 있으면 구조화된 401 응답을 반환한다")
    void authMe_failure_invalidToken() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/auth/me").cookie(
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
                get("/api/v1/auth/me").cookie(
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
                get("/api/v1/auth/me").cookie(
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
    @DisplayName("허용된 프론트 Origin의 닉네임 설정 preflight 요청을 허용한다")
    void completeNicknameSetupPreflight_success_allowedOrigin() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                options("/api/v1/auth/nickname").header(
                        HttpHeaders.ORIGIN,
                        FRONTEND_ORIGIN
                )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.POST.name()
                        )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "content-type,x-xsrf-token"
                        )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                FRONTEND_ORIGIN
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                                "true"
                        )
                );
    }

    @Test
    @DisplayName("허용된 프론트 Origin의 마지막 워크스페이스 PUT preflight 요청을 허용한다")
    void lastViewedWorkspacePreflight_success_allowedOrigin() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                options("/api/v1/members/me/last-viewed-workspace").header(
                        HttpHeaders.ORIGIN,
                        FRONTEND_ORIGIN
                )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.PUT.name()
                        )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "content-type,x-xsrf-token"
                        )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                FRONTEND_ORIGIN
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                                containsString(HttpMethod.PUT.name())
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                                "true"
                        )
                );
    }

    @Test
    @DisplayName("허용하지 않은 Origin에는 CORS 허용 헤더를 제공하지 않는다")
    void completeNicknameSetupPreflight_failure_unallowedOrigin() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                options("/api/v1/auth/nickname").header(
                        HttpHeaders.ORIGIN,
                        UNALLOWED_ORIGIN
                )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.POST.name()
                        )
        );

        // then
        result.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("인증 없이 CSRF 토큰을 조회하고 쿠키와 응답에 동일한 토큰을 반환한다")
    void csrf_success_unauthenticated() throws Exception {
        // given

        // when
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        // then
        Cookie csrfCookie = result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        JsonNode responseBody = objectMapper.readTree(
                result.getResponse()
                        .getContentAsString()
        );
        assertThat(
                responseBody.get("token")
                        .asText()
        ).isEqualTo(csrfCookie.getValue());
    }

    @Test
    @DisplayName("CSRF 토큰 조회 API에서 받은 토큰으로 닉네임 설정을 완료한다")
    void completeNicknameSetup_success_withCsrfTokenEndpoint() throws Exception {
        // given
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
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
                post("/api/v1/auth/nickname").cookie(
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
                post("/api/v1/auth/nickname").cookie(
                        new Cookie(
                                NICKNAME_COOKIE_NAME,
                                nicknameToken
                        )
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + uniqueValue("user-") + "\"}")
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("요청 권한이 없습니다"));
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
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse()
                .getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        String csrfToken = csrfCookie.getValue();

        // when
        ResultActions result = mockMvc.perform(
                post("/api/v1/auth/logout").cookie(
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
