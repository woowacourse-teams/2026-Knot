package com.knot.backend.workspace.presentation;

import static org.hamcrest.Matchers.containsInAnyOrder;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotionPageTreeApiDocumentationAcceptanceTest {
    private static final String ITEM_RESPONSE_REF = "#/components/schemas/NotionPageTreeItemResponse";
    private static final String ERROR_RESPONSE_REF = "#/components/schemas/ErrorResponse";

    private final MockMvc mockMvc;

    NotionPageTreeApiDocumentationAcceptanceTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @DisplayName("OpenAPI JSON에 Page Tree 평면 배열, 보안, 오류와 no-store 계약을 공개한다")
    @Test
    void openApi_success_notionPageTreeContract() throws Exception {
        // given
        String openApiPath = "/v3/api-docs";
        String operationPath = "$.paths['/api/v1/workspaces/{workspaceId}/notion-pages/tree'].get";
        String schemaPath = "$.components.schemas.NotionPageTreeItemResponse.properties";

        // when
        ResultActions result = mockMvc.perform(get(openApiPath));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath(operationPath + ".summary").value("Notion Page Tree 조회"))
                .andExpect(jsonPath(operationPath + ".security[0].accessTokenCookie").exists())
                .andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").doesNotExist())
                .andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'workspaceId')]").exists())
                .andExpect(
                        jsonPath(operationPath + ".parameters[?(@.name == 'workspaceId')].required")
                                .value(hasItem(true))
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['200'].content['application/json'].schema.type")
                                .value("array")
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['200'].content['application/json'].schema.items['$ref']")
                                .value(ITEM_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses.*.headers.Cache-Control")
                                .value(org.hamcrest.Matchers.hasSize(6))
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['400'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['403'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['500'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(jsonPath(schemaPath + ".id").exists())
                .andExpect(
                        jsonPath(schemaPath + ".parentPageId.type").value(
                                containsInAnyOrder(
                                        "integer",
                                        "null"
                                )
                        )
                )
                .andExpect(jsonPath(schemaPath + ".title").exists())
                .andExpect(jsonPath(schemaPath + ".position").exists())
                .andExpect(jsonPath(schemaPath + ".notionUrl").exists())
                .andExpect(jsonPath(schemaPath + ".markdownContent").doesNotExist());
    }
}
