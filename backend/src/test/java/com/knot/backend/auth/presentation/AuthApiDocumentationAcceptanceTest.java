package com.knot.backend.auth.presentation;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@Tag("acceptance")
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthApiDocumentationAcceptanceTest {
    private final MockMvc mockMvc;

    AuthApiDocumentationAcceptanceTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("회원 정보 문서는 access 인증과 성공·미인증 응답을 공개하고 내부 인증 객체를 숨긴다")
    void openApi_success_memberContract() throws Exception {
        // given
        String path = "$.paths['/api/v1/auth/me'].get";

        // when & then
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".summary").value("내 회원 정보 조회"))
                .andExpect(jsonPath(path + ".security[0].accessTokenCookie").exists())
                .andExpect(jsonPath(path + ".parameters").doesNotExist())
                .andExpect(
                        jsonPath(path + ".responses['200'].content['application/json'].schema['$ref']")
                                .value("#/components/schemas/AuthenticatedMemberResponse")
                )
                .andExpect(
                        jsonPath(path + ".responses['401'].content['application/json'].schema['$ref']")
                                .value("#/components/schemas/ErrorResponse")
                );
    }

    @Test
    @DisplayName("CSRF 문서는 로그인 없이 토큰을 조회하는 계약을 공개하고 내부 토큰 객체를 숨긴다")
    void openApi_success_csrfContract() throws Exception {
        // given
        String path = "$.paths['/api/v1/auth/csrf'].get";

        // when & then
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".summary").value("CSRF 토큰 조회"))
                .andExpect(jsonPath(path + ".security").doesNotExist())
                .andExpect(jsonPath(path + ".parameters").doesNotExist())
                .andExpect(
                        jsonPath(path + ".responses['200'].content['application/json'].schema['$ref']")
                                .value("#/components/schemas/CsrfTokenResponse")
                );
    }

    @Test
    @DisplayName("재발급 문서는 refresh 쿠키와 CSRF를 요구하고 access 인증 없이 204를 반환한다")
    void openApi_success_refreshContract() throws Exception {
        // given
        String path = "$.paths['/api/v1/auth/refresh'].post";

        // when & then
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".summary").value("인증 토큰 재발급"))
                .andExpect(jsonPath(path + ".security").doesNotExist())
                .andExpect(
                        jsonPath(path + ".parameters[?(@.name == '__Host-KNOT_REFRESH_TOKEN')].in")
                                .value(hasItem("cookie"))
                )
                .andExpect(jsonPath(path + ".parameters[?(@.name == 'X-XSRF-TOKEN')].required").value(hasItem(true)))
                .andExpect(jsonPath(path + ".responses['204'].content").doesNotExist())
                .andExpect(jsonPath(path + ".responses['204'].headers['Set-Cookie']").exists())
                .andExpect(jsonPath(path + ".responses['401'].description").value("UNAUTHENTICATED: 재발급 불가"))
                .andExpect(jsonPath(path + ".responses['403'].description").value("CSRF_INVALID: CSRF 검증 실패"));
    }

    @Test
    @DisplayName("닉네임 문서는 가입 요청 본문과 쿠키 발급·실패 응답을 공개한다")
    void openApi_success_nicknameContract() throws Exception {
        // given
        String path = "$.paths['/api/v1/auth/nickname'].post";

        // when & then
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".summary").value("닉네임 설정"))
                .andExpect(jsonPath(path + ".security").doesNotExist())
                .andExpect(
                        jsonPath(path + ".requestBody.content['application/json'].schema['$ref']")
                                .value("#/components/schemas/CompleteNicknameRequest")
                )
                .andExpect(jsonPath(path + ".responses['204'].content").doesNotExist())
                .andExpect(jsonPath(path + ".responses['400']").exists())
                .andExpect(jsonPath(path + ".responses['401']").exists())
                .andExpect(jsonPath(path + ".responses['403']").exists())
                .andExpect(jsonPath(path + ".responses['409']").exists());
    }

    @Test
    @DisplayName("로그아웃 문서는 redirect 대신 쿠키 만료와 204·CSRF 실패 응답을 공개한다")
    void openApi_success_logoutContract() throws Exception {
        // given
        String path = "$.paths['/api/v1/auth/logout'].post";

        // when & then
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".responses['204'].headers['Set-Cookie']").exists())
                .andExpect(jsonPath(path + ".responses['302']").doesNotExist())
                .andExpect(jsonPath(path + ".responses['403']").exists());
    }
}
