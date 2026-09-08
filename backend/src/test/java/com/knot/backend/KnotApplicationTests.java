package com.knot.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
class KnotApplicationTests {
    private static final String FRONTEND_ORIGIN = "https://knoted.kr";
    private static final String LOCAL_FRONTEND_ORIGIN = "http://localhost:3000";
    private static final String UNALLOWED_ORIGIN = "https://attacker.example";

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;

    KnotApplicationTests(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            JdbcClient jdbcClient
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.jdbcClient = jdbcClient;
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
    @DisplayName("Bearer 토큰이 있으면 인증된 member 정보를 조회한다")
    void authMe_success() throws Exception {
        // given
        long memberId = saveMember();
        AuthenticatedMember member = AuthenticatedMember.of(
                memberId,
                "octocat",
                "https://example.com/avatar"
        );
        String token = authTokenProvider.issue(member);

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/auth/me").header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + token
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId))
                .andExpect(jsonPath("$.nickname").value("octocat"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/avatar"));
    }

    @Test
    @DisplayName("서명이 유효해도 회원이 없는 토큰이면 401을 준다")
    void authMe_failure_memberNotFound() throws Exception {
        // given
        long deletedMemberId = saveMember();
        jdbcClient.sql("DELETE FROM members WHERE id = :id")
                .param(
                        "id",
                        deletedMemberId
                )
                .update();
        String token = authTokenProvider.issue(
                AuthenticatedMember.of(
                        deletedMemberId,
                        "octocat",
                        null
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/auth/me").header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + token
                )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
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
    @DisplayName("잘못된 Bearer 토큰이면 구조화된 401 응답을 반환한다")
    void authMe_failure_invalidToken() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/auth/me").header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer invalid-token"
                )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("만료된 Bearer 토큰이면 인증되지 않은 요청으로 처리한다")
    void authMe_failure_expiredToken() throws Exception {
        // given
        String expiredToken = expiredAccessToken();

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/auth/me").header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + expiredToken
                )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("온보딩 토큰을 Bearer로 보내면 인증되지 않는다")
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
                get("/api/v1/auth/me").header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + nicknameToken
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
                                "content-type,authorization"
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
                                HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                                containsStringIgnoringCase(HttpHeaders.AUTHORIZATION)
                        )
                )
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
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
                                "content-type,authorization"
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
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
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
    @DisplayName("localhost 프론트 Origin의 인증 요청에 CORS 허용 헤더를 준다")
    void authMe_success_localFrontendOrigin() throws Exception {
        // given
        String token = authTokenProvider.issue(
                AuthenticatedMember.of(
                        saveMember(),
                        "octocat",
                        null
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/auth/me").header(
                        HttpHeaders.ORIGIN,
                        LOCAL_FRONTEND_ORIGIN
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                LOCAL_FRONTEND_ORIGIN
                        )
                )
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("온보딩 토큰을 Bearer로 보내면 닉네임 설정을 마치고 액세스 토큰을 본문으로 받는다")
    void completeNicknameSetup_success() throws Exception {
        // given
        String nickname = uniqueValue("user-");
        String onboardingToken = authTokenProvider.issueNickname(
                OAuthUser.of(
                        OAuthProvider.GITHUB,
                        uniqueValue("onboarding-user-"),
                        null
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                post("/api/v1/auth/nickname").header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + onboardingToken
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}")
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("Authorization 헤더가 없는 닉네임 설정 요청은 401로 거부한다")
    void completeNicknameSetup_failure_missingOnboardingToken() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                post("/api/v1/auth/nickname").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + uniqueValue("user-") + "\"}")
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_JWT"));
    }

    @Test
    @DisplayName("로그아웃은 쿠키를 심지 않고 204만 돌려준다")
    void logout_success() throws Exception {
        // given
        String token = authTokenProvider.issue(
                AuthenticatedMember.of(
                        1L,
                        "octocat",
                        null
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                post("/api/v1/auth/logout").header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + token
                )
        );

        // then
        result.andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private String expiredAccessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-jwt-secret-012345678901234567890123456789");
        properties.setExpiration(Duration.ofHours(1));
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

    /** 액세스 토큰은 회원이 있어야 인증되므로(기획서 5.1 회원 확인) 토큰의 subject를 실제 행으로 만든다 */
    private long saveMember() {
        return jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES ('octocat', 'https://example.com/avatar')
                RETURNING id
                """)
                .query(Long.class)
                .single();
    }
}
