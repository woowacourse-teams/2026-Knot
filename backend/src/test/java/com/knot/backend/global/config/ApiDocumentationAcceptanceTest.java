package com.knot.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ApiDocumentationAcceptanceTest {
    private final MockMvc mockMvc;
    private final OAuth2LoginProperties oauth2LoginProperties;

    ApiDocumentationAcceptanceTest(
            MockMvc mockMvc,
            OAuth2LoginProperties oauth2LoginProperties
    ) {
        this.mockMvc = mockMvc;
        this.oauth2LoginProperties = oauth2LoginProperties;
    }

    @Test
    @DisplayName("OAuth 닉네임 설정 redirect 기본값은 온보딩 경로로 바인딩된다")
    void oauth2LoginProperties_success_bindsOnboardingRedirect() {
        // given

        // when
        String nicknameRedirectUri = oauth2LoginProperties.getNicknameRedirectUri();

        // then
        assertThat(nicknameRedirectUri).isEqualTo("/onboarding");
    }

    @Test
    @DisplayName("개발 프로파일에서는 Swagger UI를 공개한다")
    void swaggerUi_success_developmentProfile() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(get("/swagger-ui.html"));

        // then
        result.andExpect(status().is3xxRedirection())
                .andExpect(
                        header().string(
                                "Location",
                                "/swagger-ui/index.html"
                        )
                );
    }

    @Test
    @DisplayName("개발 프로파일에서는 OpenAPI JSON과 인증 태그를 공개한다")
    void openApi_success_developmentProfile() throws Exception {
        // given
        String authorizationLocationPath = "$.paths['/oauth2/authorization/{registrationId}']"
                + ".get.responses['302'].headers.Location";

        // when
        ResultActions result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.tags[0].name").value("인증"))
                .andExpect(jsonPath("$.tags[0].description").value("회원가입, 로그인, 리프레쉬, 로그아웃, 확인"))
                .andExpect(jsonPath("$.paths['/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/auth/csrf'].get").exists())
                .andExpect(jsonPath("$.paths['/auth/nickname'].post").exists())
                .andExpect(jsonPath(authorizationLocationPath).exists());
    }

    @Test
    @DisplayName("OpenAPI JSON에 워크스페이스 초대 발급·조회·재발급 계약을 공개한다")
    void openApi_success_workspaceInvitationContract() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/workspaces/{workspaceId}/invitations'].post").exists())
                .andExpect(jsonPath("$.paths['/workspaces/{workspaceId}/invitations'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/workspaces/{workspaceId}/invitations'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/workspaces/{workspaceId}/invitation'].get").exists())
                .andExpect(jsonPath("$.paths['/workspaces/{workspaceId}/invitations/reissue'].post").exists())
                .andExpect(jsonPath("$.components.schemas.WorkspaceInvitationResponse.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.WorkspaceInvitationResponse.properties.linkToken").exists())
                .andExpect(jsonPath("$.components.schemas.WorkspaceInvitationResponse.properties.expiresAt").exists());
    }

    @Test
    @DisplayName("Swagger 설정은 외부 validator와 API 실행 기능을 사용하지 않는다")
    void swaggerConfig_success_disablesExternalRequestsAndTryItOut() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(get("/v3/api-docs/swagger-config"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(jsonPath("$.validatorUrl").value("none"))
                .andExpect(jsonPath("$.supportedSubmitMethods").isArray())
                .andExpect(jsonPath("$.supportedSubmitMethods").isEmpty());
    }
}
