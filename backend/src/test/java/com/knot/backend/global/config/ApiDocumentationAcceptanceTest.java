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
        String createWorkspacePath = "$.paths['/workspaces'].post";
        String detailWorkspacePath = "$.paths['/workspaces/{workspaceId}'].get";
        String authDescriptionPath = "$.tags[?(@.name == '인증')].description";
        String workspaceDescriptionPath = "$.tags[?(@.name == '워크스페이스')].description";
        String workspaceTagsPath = createWorkspacePath + ".tags";
        String authDescription = "회원가입, 로그인, 리프레쉬, 로그아웃, 확인";
        String workspaceDescription = "워크스페이스 생성 및 조회";
        String oauthAuthorizationPath = "$.paths['/oauth2/authorization/{registrationId}']";
        String oauthLocationPath = oauthAuthorizationPath + ".get.responses['302'].headers.Location";
        String workspaceRequestSchemaPath = "$.components.schemas.WorkspaceCreateRequest";
        String workspaceNameSchemaPath = workspaceRequestSchemaPath + ".properties.name";
        String createWorkspaceResponseRef = "#/components/schemas/WorkspaceCreateResponse";
        String detailWorkspaceResponseRef = "#/components/schemas/WorkspaceDetailResponse";
        String errorResponseRef = "#/components/schemas/ErrorResponse";

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
                .andExpect(jsonPath(createWorkspacePath).exists())
                .andExpect(jsonPath(detailWorkspacePath).exists())
                .andExpect(jsonPath(createWorkspacePath + ".summary").value("워크스페이스 생성"))
                .andExpect(jsonPath(detailWorkspacePath + ".summary").value("워크스페이스 단건 조회"))
                .andExpect(
                        jsonPath(createWorkspacePath + ".responses['201'].content['application/json'].schema['$ref']")
                                .value(createWorkspaceResponseRef)
                )
                .andExpect(
                        jsonPath(createWorkspacePath + ".responses['400'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(createWorkspacePath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(createWorkspacePath + ".responses['403'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(jsonPath(createWorkspacePath + ".security[0].accessTokenCookie").exists())
                .andExpect(jsonPath(createWorkspacePath + ".security[0].csrfTokenHeader").exists())
                .andExpect(jsonPath(workspaceTagsPath).value(hasItem("워크스페이스")))
                .andExpect(jsonPath("$.components.securitySchemes.accessTokenCookie.in").value("cookie"))
                .andExpect(
                        jsonPath("$.components.securitySchemes.accessTokenCookie.name")
                                .value("__Host-KNOT_ACCESS_TOKEN")
                )
                .andExpect(jsonPath("$.components.securitySchemes.csrfTokenHeader.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfTokenHeader.name").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceCreateRequest").exists())
                .andExpect(jsonPath(workspaceRequestSchemaPath + ".required").value(hasItem("name")))
                .andExpect(jsonPath(workspaceNameSchemaPath + ".maxLength").value(20))
                .andExpect(jsonPath(workspaceNameSchemaPath + ".pattern").value("^(?=.*[가-힣A-Za-z])[가-힣A-Za-z ]+$"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceCreateResponse").exists())
                .andExpect(
                        jsonPath(detailWorkspacePath + ".responses['200'].content['application/json'].schema['$ref']")
                                .value(detailWorkspaceResponseRef)
                )
                .andExpect(
                        jsonPath(detailWorkspacePath + ".responses['400'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(detailWorkspacePath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(detailWorkspacePath + ".responses['403'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(detailWorkspacePath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(jsonPath(detailWorkspacePath + ".security[*].accessTokenCookie").exists())
                .andExpect(jsonPath("$.components.schemas.WorkspaceDetailResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                .andExpect(jsonPath(oauthLocationPath).exists());
    }

    @Test
    @DisplayName("OpenAPI JSON에 워크스페이스 초대 발급·조회·재발급·미리보기 계약을 공개한다")
    void openApi_success_workspaceInvitationContract() throws Exception {
        // given
        String openApiPath = "/v3/api-docs";
        String issuePath = "$.paths['/workspaces/{workspaceId}/invitations'].post";
        String getPath = "$.paths['/workspaces/{workspaceId}/invitation'].get";
        String reissuePath = "$.paths['/workspaces/{workspaceId}/invitations/reissue'].post";
        String invitationResponseRef = "#/components/schemas/WorkspaceInvitationResponse";
        String previewPath = "$.paths['/invitations/{tokenOrCode}'].get";
        String errorResponseRef = "#/components/schemas/ErrorResponse";

        // when
        ResultActions result = mockMvc.perform(get(openApiPath));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath(issuePath + ".summary").value("워크스페이스 초대 발급"))
                .andExpect(jsonPath(issuePath + ".security[*].accessTokenCookie").exists())
                .andExpect(jsonPath(issuePath + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").exists())
                .andExpect(
                        jsonPath(issuePath + ".parameters[?(@.name == 'X-XSRF-TOKEN')].required").value(hasItem(true))
                )
                .andExpect(
                        jsonPath(issuePath + ".parameters[?(@.name == 'X-XSRF-TOKEN')].schema.type")
                                .value(hasItem("string"))
                )
                .andExpect(
                        jsonPath(issuePath + ".responses['200'].content['application/json'].schema['$ref']")
                                .value(invitationResponseRef)
                )
                .andExpect(jsonPath(issuePath + ".responses['201'].headers.Location").exists())
                .andExpect(jsonPath(issuePath + ".responses['201'].headers.Location.schema.format").value("uri"))
                .andExpect(
                        jsonPath(issuePath + ".responses['201'].content['application/json'].schema['$ref']")
                                .value(invitationResponseRef)
                )
                .andExpect(
                        jsonPath(issuePath + ".responses['400'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(issuePath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(issuePath + ".responses['403'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(issuePath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(issuePath + ".responses['500'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(jsonPath(getPath + ".summary").value("워크스페이스 초대 조회"))
                .andExpect(jsonPath(getPath + ".security[*].accessTokenCookie").exists())
                .andExpect(jsonPath(getPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").doesNotExist())
                .andExpect(
                        jsonPath(getPath + ".responses['200'].content['application/json'].schema['$ref']")
                                .value(invitationResponseRef)
                )
                .andExpect(
                        jsonPath(getPath + ".responses['400'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(getPath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(getPath + ".responses['403'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(getPath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(getPath + ".responses['500'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(jsonPath(reissuePath + ".summary").value("워크스페이스 초대 재발급"))
                .andExpect(jsonPath(reissuePath + ".security[*].accessTokenCookie").exists())
                .andExpect(jsonPath(reissuePath + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").exists())
                .andExpect(
                        jsonPath(reissuePath + ".parameters[?(@.name == 'X-XSRF-TOKEN')].required").value(hasItem(true))
                )
                .andExpect(
                        jsonPath(reissuePath + ".parameters[?(@.name == 'X-XSRF-TOKEN')].schema.type")
                                .value(hasItem("string"))
                )
                .andExpect(jsonPath(reissuePath + ".responses['201'].headers.Location").exists())
                .andExpect(jsonPath(reissuePath + ".responses['201'].headers.Location.schema.format").value("uri"))
                .andExpect(
                        jsonPath(reissuePath + ".responses['201'].content['application/json'].schema['$ref']")
                                .value(invitationResponseRef)
                )
                .andExpect(
                        jsonPath(reissuePath + ".responses['400'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(reissuePath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(reissuePath + ".responses['403'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(reissuePath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(reissuePath + ".responses['500'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(jsonPath(previewPath + ".security").doesNotExist())
                .andExpect(
                        jsonPath(previewPath + ".responses['200'].content['application/json'].schema['$ref']").exists()
                )
                .andExpect(
                        jsonPath(previewPath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(jsonPath(previewPath + ".responses['429'].headers.Retry-After").exists())
                .andExpect(
                        jsonPath(previewPath + ".responses['429'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(jsonPath("$.components.schemas.WorkspaceInvitationResponse.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.WorkspaceInvitationResponse.properties.linkToken").exists())
                .andExpect(jsonPath("$.components.schemas.WorkspaceInvitationResponse.properties.expiresAt").exists())
                .andExpect(
                        jsonPath("$.components.schemas.WorkspaceInvitationPreviewResponse.properties.workspaceId")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.components.schemas.WorkspaceInvitationPreviewResponse.properties.workspaceName")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.components.schemas.WorkspaceInvitationPreviewResponse.properties.code")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.components.schemas.WorkspaceInvitationPreviewResponse.properties.linkToken")
                                .doesNotExist()
                );
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
