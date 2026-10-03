package com.knot.backend.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthNicknameAcceptanceTest {
    private static final String NICKNAME_COOKIE_NAME = "KNOT_NICKNAME_TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcTemplate jdbcTemplate;

    AuthNicknameAcceptanceTest(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("유효한 닉네임 설정은 204와 access·refresh 쿠키를 발급하고 온보딩 쿠키를 만료한다")
    void completeNicknameSetup_success_issuesAuthenticationCookies() throws Exception {
        // given
        String externalId = uniqueExternalId();
        String nicknameToken = issueNicknameToken(externalId);
        Cookie csrfCookie = csrfCookie();

        // when
        MvcResult result = submitNickname(
                nicknameToken,
                "valid-user",
                csrfCookie
        ).andExpect(status().isNoContent())
                .andExpect(content().string(emptyString()))
                .andReturn();

        // then
        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getCookie("KNOT_ACCESS_TOKEN")).isNotNull();
        assertThat(
                response.getCookie("KNOT_ACCESS_TOKEN")
                        .getMaxAge()
        ).isEqualTo(3600);
        assertThat(response.getCookie("KNOT_REFRESH_TOKEN")).isNotNull();
        assertThat(
                response.getCookie("KNOT_REFRESH_TOKEN")
                        .getMaxAge()
        ).isPositive()
                .isLessThanOrEqualTo(7 * 86400);
        assertThat(response.getCookie(NICKNAME_COOKIE_NAME)).isNotNull();
        assertThat(
                response.getCookie(NICKNAME_COOKIE_NAME)
                        .getMaxAge()
        ).isZero();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(3);
        assertThat(countIdentity(externalId)).isEqualTo(1);
        assertThat(countSessions(externalId)).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidNicknames")
    @DisplayName("허용되지 않은 닉네임 요청은 400으로 거부한다")
    void completeNicknameSetup_failure_invalidNickname(String nickname) throws Exception {
        // given
        String externalId = uniqueExternalId();
        Cookie csrfCookie = csrfCookie();

        // when
        ResultActions result = submitNickname(
                issueNicknameToken(externalId),
                nickname,
                csrfCookie
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(countIdentity(externalId)).isZero();
        assertThat(countSessions(externalId)).isZero();
    }

    @Test
    @DisplayName("온보딩 쿠키가 없으면 401 INVALID_JWT를 반환한다")
    void completeNicknameSetup_failure_missingNicknameToken() throws Exception {
        // given
        Cookie csrfCookie = csrfCookie();

        // when
        ResultActions result = submitNickname(
                null,
                "valid-user",
                csrfCookie
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_JWT"));
    }

    @Test
    @DisplayName("일반 access token은 온보딩 쿠키로 사용할 수 없다")
    void completeNicknameSetup_failure_accessTokenAsNicknameToken() throws Exception {
        // given
        String accessToken = authTokenProvider.issue(
                AuthenticatedMember.of(
                        1L,
                        "existing-user",
                        null
                )
        );
        Cookie csrfCookie = csrfCookie();

        // when
        ResultActions result = submitNickname(
                accessToken,
                "valid-user",
                csrfCookie
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_JWT"));
    }

    @Test
    @DisplayName("JSON 형식이 잘못되면 400 INVALID_REQUEST_BODY를 반환한다")
    void completeNicknameSetup_failure_malformedRequestBody() throws Exception {
        // given
        Cookie csrfCookie = csrfCookie();

        // when
        ResultActions result = mockMvc.perform(
                post("/api/v1/auth/nickname").cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{")
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    @DisplayName("CSRF 쿠키와 헤더가 다르면 403 CSRF_INVALID를 반환한다")
    void completeNicknameSetup_failure_invalidCsrf() throws Exception {
        // given
        String nicknameToken = issueNicknameToken(uniqueExternalId());
        Cookie csrfCookie = csrfCookie();

        // when
        ResultActions result = mockMvc.perform(
                post("/api/v1/auth/nickname").cookie(
                        new Cookie(
                                NICKNAME_COOKIE_NAME,
                                nicknameToken
                        ),
                        csrfCookie
                )
                        .header(
                                CSRF_HEADER_NAME,
                                "invalid-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"valid-user\"}")
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("같은 OAuth 계정의 가입 완료 재요청은 409로 거부하고 기존 자료를 보존한다")
    void completeNicknameSetup_failure_alreadyCompleted() throws Exception {
        // given
        String externalId = uniqueExternalId();
        String nicknameToken = issueNicknameToken(externalId);
        Cookie firstCsrfCookie = csrfCookie();
        submitNickname(
                nicknameToken,
                "first-name",
                firstCsrfCookie
        ).andExpect(status().isNoContent());

        // when
        ResultActions result = submitNickname(
                nicknameToken,
                "second-name",
                csrfCookie()
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_SETUP_ALREADY_COMPLETED"));
        assertThat(countIdentity(externalId)).isEqualTo(1);
        assertThat(countSessions(externalId)).isEqualTo(1);
    }

    private ResultActions submitNickname(
            String nicknameToken,
            String nickname,
            Cookie csrfCookie
    ) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/auth/nickname").cookie(csrfCookie)
                .header(
                        CSRF_HEADER_NAME,
                        csrfCookie.getValue()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}");
        if (nicknameToken != null) {
            request.cookie(
                    new Cookie(
                            NICKNAME_COOKIE_NAME,
                            nicknameToken
                    )
            );
        }
        return mockMvc.perform(request);
    }

    private Cookie csrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);
    }

    private String issueNicknameToken(String externalId) {
        return authTokenProvider.issueNickname(
                OAuthUser.of(
                        OAuthProvider.GITHUB,
                        externalId,
                        null
                )
        );
    }

    private String uniqueExternalId() {
        return "nickname-" + UUID.randomUUID();
    }

    private int countIdentity(String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth_identities WHERE provider = 'GITHUB' AND provider_user_id = ?",
                Integer.class,
                externalId
        );
    }

    private int countSessions(String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_sessions auth_session " + "JOIN oauth_identities oauth_identity "
                        + "ON oauth_identity.member_id = auth_session.member_id "
                        + "WHERE oauth_identity.provider = 'GITHUB' AND oauth_identity.provider_user_id = ?",
                Integer.class,
                externalId
        );
    }

    private static Stream<String> invalidNicknames() {
        return Stream.of(
                "",
                " ",
                "nickname1",
                "user_name",
                "two words",
                "a".repeat(21)
        );
    }
}
