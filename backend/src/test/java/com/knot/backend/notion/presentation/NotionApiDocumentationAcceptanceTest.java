package com.knot.backend.notion.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotionApiDocumentationAcceptanceTest {
    private static final String ERROR_RESPONSE_REF = "#/components/schemas/ErrorResponse";
    private static final String AUTHORIZATION_RESPONSE_REF = "#/components/schemas/NotionOAuthAuthorizationResponse";
    private static final String STATUS_RESPONSE_REF = "#/components/schemas/NotionConnectionStatusResponse";

    private final MockMvc mockMvc;

    NotionApiDocumentationAcceptanceTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @DisplayName("OpenAPI JSON에 Notion OAuth 시작 계약을 공개한다")
    @Test
    void openApi_success_notionOAuthStartContract() throws Exception {
        // given
        String openApiPath = "/v3/api-docs";
        String startPath = "$.paths['/workspaces/{workspaceId}/notion-oauth-authorizations'].post";

        // when
        ResultActions result = mockMvc.perform(get(openApiPath));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath(startPath + ".summary").value("Notion OAuth 연결 시작"))
                .andExpect(jsonPath(startPath + ".security[0].accessTokenCookie").exists())
                .andExpect(jsonPath(startPath + ".security[0].csrfTokenHeader").exists())
                .andExpect(jsonPath(startPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").exists())
                .andExpect(
                        jsonPath(startPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')].required").value(hasItem(true))
                )
                .andExpect(
                        jsonPath(startPath + ".responses['201'].content['application/json'].schema['$ref']")
                                .value(AUTHORIZATION_RESPONSE_REF)
                )
                .andExpect(jsonPath(startPath + ".responses['201'].headers.Cache-Control").exists())
                .andExpect(
                        jsonPath(startPath + ".responses['400'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(startPath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(startPath + ".responses['403'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(startPath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(startPath + ".responses['500'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                );
    }

    @DisplayName("OpenAPI JSON에 Notion OAuth callback 공개 redirect 계약을 공개한다")
    @Test
    void openApi_success_notionOAuthCallbackContract() throws Exception {
        // given
        String openApiPath = "/v3/api-docs";
        String callbackPath = "$.paths['/notion/oauth/callback'].get";

        // when
        ResultActions result = mockMvc.perform(get(openApiPath));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath(callbackPath + ".summary").value("Notion OAuth callback 처리"))
                .andExpect(jsonPath(callbackPath + ".security").isArray())
                .andExpect(jsonPath(callbackPath + ".security").isEmpty())
                .andExpect(jsonPath(callbackPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").doesNotExist())
                .andExpect(jsonPath(callbackPath + ".responses['303'].headers.Location").exists())
                .andExpect(jsonPath(callbackPath + ".responses['303'].headers.Cache-Control").exists())
                .andExpect(
                        jsonPath(callbackPath + ".responses['500'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                );
    }

    @DisplayName("OpenAPI JSON에 Notion connection 상태 조회 계약을 공개한다")
    @Test
    void openApi_success_notionConnectionStatusContract() throws Exception {
        // given
        String openApiPath = "/v3/api-docs";
        String statusPath = "$.paths['/workspaces/{workspaceId}/notion-connection'].get";

        // when
        ResultActions result = mockMvc.perform(get(openApiPath));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath(statusPath + ".summary").value("Notion 연결 상태 조회"))
                .andExpect(jsonPath(statusPath + ".security[0].accessTokenCookie").exists())
                .andExpect(jsonPath(statusPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").doesNotExist())
                .andExpect(
                        jsonPath(statusPath + ".responses['200'].content['application/json'].schema['$ref']")
                                .value(STATUS_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(statusPath + ".responses['400'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(statusPath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(statusPath + ".responses['403'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(statusPath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                );
    }

    @DisplayName("OpenAPI JSON은 Notion secret 예시를 공개하지 않는다")
    @Test
    void openApi_success_doesNotExposeSecretExamples() throws Exception {
        // given
        String openApiPath = "/v3/api-docs";

        // when
        MvcResult result = mockMvc.perform(get(openApiPath))
                .andExpect(status().isOk())
                .andReturn();

        // then
        String openApiJson = result.getResponse()
                .getContentAsString();
        assertThat(openApiJson).doesNotContain(
                "test-notion-client-secret",
                "notion-access-token",
                "notion-refresh-token",
                "state-hash-key",
                "encryption-key"
        );
    }
}
