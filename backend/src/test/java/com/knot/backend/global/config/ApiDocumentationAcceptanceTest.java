package com.knot.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
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
        String expectedRedirectUri = "/onboarding";

        // when
        String actualRedirectUri = oauth2LoginProperties.getNicknameRedirectUri();

        // then
        assertThat(actualRedirectUri).isEqualTo(expectedRedirectUri);
    }

    @Test
    @DisplayName("개발 프로파일에서는 Swagger UI를 공개한다")
    void swaggerUi_success_developmentProfile() throws Exception {
        // given
        String swaggerUiPath = "/swagger-ui.html";

        // when
        ResultActions result = mockMvc.perform(get(swaggerUiPath));

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
    @DisplayName("개발 프로파일에서는 OpenAPI JSON에 인증과 워크스페이스 계약을 공개한다")
    void openApi_success_developmentProfile() throws Exception {
        // given
        String openApiPath = "/v3/api-docs";
        String authDescriptionPath = "$.tags[?(@.name == '인증')].description";
        String workspaceDescriptionPath = "$.tags[?(@.name == '워크스페이스')].description";
        String workspaceTagsPath = "$.paths['/workspaces'].post.tags";
        String authDescription = "회원가입, 로그인, 리프레쉬, 로그아웃, 확인";
        String workspaceDescription = "워크스페이스 생성 및 관리";
        String oauthAuthorizationPath = "$.paths['/oauth2/authorization/{registrationId}']";
        String oauthLocationPath = oauthAuthorizationPath + ".get.responses['302'].headers.Location";

        // when
        ResultActions result = mockMvc.perform(get(openApiPath));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(
                        jsonPath("$.tags[*].name").value(
                                hasItems(
                                        "인증",
                                        "워크스페이스"
                                )
                        )
                )
                .andExpect(jsonPath(authDescriptionPath).value(hasItem(authDescription)))
                .andExpect(jsonPath(workspaceDescriptionPath).value(hasItem(workspaceDescription)))
                .andExpect(jsonPath("$.paths['/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/auth/csrf'].get").exists())
                .andExpect(jsonPath("$.paths['/auth/nickname'].post").exists())
                .andExpect(jsonPath("$.paths['/workspaces'].post").exists())
                .andExpect(jsonPath(workspaceTagsPath).value(hasItem("워크스페이스")))
                .andExpect(jsonPath("$.components.schemas.WorkspaceCreateRequest").exists())
                .andExpect(jsonPath("$.components.schemas.WorkspaceCreateResponse").exists())
                .andExpect(jsonPath(oauthLocationPath).exists());
    }

    @Test
    @DisplayName("Swagger 설정은 외부 validator와 API 실행 기능을 사용하지 않는다")
    void swaggerConfig_success_disablesExternalRequestsAndTryItOut() throws Exception {
        // given
        String swaggerConfigPath = "/v3/api-docs/swagger-config";

        // when
        ResultActions result = mockMvc.perform(get(swaggerConfigPath));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(jsonPath("$.validatorUrl").value("none"))
                .andExpect(jsonPath("$.supportedSubmitMethods").isArray())
                .andExpect(jsonPath("$.supportedSubmitMethods").isEmpty());
    }
}
