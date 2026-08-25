package com.knot.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";

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
                42L,
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
                .andExpect(jsonPath("$.githubId").value(42))
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
    @DisplayName("로그아웃하면 JWT 쿠키를 만료시킨다")
    void logout_success() throws Exception {
        // given
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                42L,
                "octocat",
                null
        );
        String token = authTokenProvider.issue(member);

        // when
        ResultActions result = mockMvc.perform(
                post("/logout").with(csrf())
                        .cookie(
                                new Cookie(
                                        JWT_COOKIE_NAME,
                                        token
                                )
                        )
        );

        // then
        result.andExpect(status().isFound())
                .andExpect(
                        header().string(
                                "Set-Cookie",
                                containsString("Max-Age=0")
                        )
                )
                .andExpect(
                        header().string(
                                "Set-Cookie",
                                containsString(JWT_COOKIE_NAME + "=")
                        )
                );
    }
}
