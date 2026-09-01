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
class NotionImportApiDocumentationAcceptanceTest {
    private static final String RESPONSE_REF = "#/components/schemas/NotionImportStatusResponse";
    private static final String START_RESPONSE_REF = "#/components/schemas/NotionImportStartResponse";
    private static final String ERROR_RESPONSE_REF = "#/components/schemas/ErrorResponse";

    private final MockMvc mockMvc;

    NotionImportApiDocumentationAcceptanceTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @DisplayName("OpenAPI JSON에 Import 상태 조회의 보안, 응답, nullable, no-store 계약을 공개한다")
    @Test
    void openApi_success_notionImportStatusContract() throws Exception {
        // given
        String openApiPath = "/v3/api-docs";
        String operationPath = "$.paths['/api/v1/imports/{importRunId}'].get";
        String schemaPath = "$.components.schemas.NotionImportStatusResponse.properties";

        // when
        ResultActions result = mockMvc.perform(get(openApiPath));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath(operationPath + ".summary").value("Notion Import 상태 조회"))
                .andExpect(jsonPath(operationPath + ".security[0].accessTokenCookie").exists())
                .andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").doesNotExist())
                .andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'importRunId')]").exists())
                .andExpect(
                        jsonPath(operationPath + ".parameters[?(@.name == 'importRunId')].required")
                                .value(hasItem(true))
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['200'].content['application/json'].schema['$ref']")
                                .value(RESPONSE_REF)
                )
                .andExpect(jsonPath(operationPath + ".responses['200'].headers.Cache-Control").exists())
                .andExpect(
                        jsonPath(operationPath + ".responses['400'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(jsonPath(operationPath + ".responses['403']").doesNotExist())
                .andExpect(jsonPath(schemaPath + ".id").exists())
                .andExpect(
                        jsonPath(schemaPath + ".status.enum").value(
                                containsInAnyOrder(
                                        "PENDING",
                                        "RUNNING",
                                        "COMPLETED",
                                        "FAILED"
                                )
                        )
                )
                .andExpect(
                        jsonPath(schemaPath + ".totalPageCount.type").value(
                                containsInAnyOrder(
                                        "integer",
                                        "null"
                                )
                        )
                )
                .andExpect(jsonPath(schemaPath + ".processedPageCount").exists())
                .andExpect(
                        jsonPath(schemaPath + ".failureReason.type").value(
                                containsInAnyOrder(
                                        "string",
                                        "null"
                                )
                        )
                )
                .andExpect(jsonPath(schemaPath + ".createdAt").exists())
                .andExpect(
                        jsonPath(schemaPath + ".startedAt.type").value(
                                containsInAnyOrder(
                                        "string",
                                        "null"
                                )
                        )
                )
                .andExpect(
                        jsonPath(schemaPath + ".completedAt.type").value(
                                containsInAnyOrder(
                                        "string",
                                        "null"
                                )
                        )
                );
    }

    @DisplayName("OpenAPI JSON에 수동 Import 시작의 빈 요청, 보안, 성공과 충돌 응답 계약을 공개한다")
    @Test
    void openApi_success_notionImportStartContract() throws Exception {
        // given
        String openApiPath = "/v3/api-docs";
        String operationPath = "$.paths['/api/v1/workspaces/{workspaceId}/imports'].post";
        String schemaPath = "$.components.schemas.NotionImportStartResponse";

        // when
        ResultActions result = mockMvc.perform(get(openApiPath));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath(operationPath + ".summary").value("수동 Notion Import 시작"))
                .andExpect(jsonPath(operationPath + ".security[0].accessTokenCookie").exists())
                .andExpect(jsonPath(operationPath + ".requestBody").doesNotExist())
                .andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'workspaceId')]").exists())
                .andExpect(
                        jsonPath(operationPath + ".parameters[?(@.name == 'workspaceId')].required")
                                .value(hasItem(true))
                )
                .andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").exists())
                .andExpect(
                        jsonPath(operationPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')].required")
                                .value(hasItem(true))
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['202'].content['application/json'].schema['$ref']")
                                .value(START_RESPONSE_REF)
                )
                .andExpect(jsonPath(operationPath + ".responses['202'].headers.Location").exists())
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
                .andExpect(jsonPath(operationPath + ".responses['409'].headers.Location").exists())
                .andExpect(
                        jsonPath(
                                operationPath + ".responses['409'].content['application/json'].schema.oneOf[*]['$ref']"
                        ).value(
                                containsInAnyOrder(
                                        START_RESPONSE_REF,
                                        ERROR_RESPONSE_REF
                                )
                        )
                )
                .andExpect(jsonPath(schemaPath + ".properties.id").exists());
    }

    @DisplayName("OpenAPI JSON에 실패 Import 재시도의 빈 요청, 보안, 성공과 충돌 응답 계약을 공개한다")
    @Test
    void openApi_success_notionImportRetryContract() throws Exception {
        // given
        String openApiPath = "/v3/api-docs";
        String operationPath = "$.paths['/api/v1/imports/{importRunId}/retry'].post";

        // when
        ResultActions result = mockMvc.perform(get(openApiPath));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath(operationPath + ".summary").value("실패한 Notion Import 재시도"))
                .andExpect(jsonPath(operationPath + ".security[0].accessTokenCookie").exists())
                .andExpect(jsonPath(operationPath + ".requestBody").doesNotExist())
                .andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'importRunId')]").exists())
                .andExpect(
                        jsonPath(operationPath + ".parameters[?(@.name == 'importRunId')].required")
                                .value(hasItem(true))
                )
                .andExpect(jsonPath(operationPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").exists())
                .andExpect(
                        jsonPath(operationPath + ".parameters[?(@.name == 'X-XSRF-TOKEN')].required")
                                .value(hasItem(true))
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['202'].content['application/json'].schema['$ref']")
                                .value(START_RESPONSE_REF)
                )
                .andExpect(jsonPath(operationPath + ".responses['202'].headers.Location").exists())
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
                .andExpect(jsonPath(operationPath + ".responses['409'].headers.Location").exists())
                .andExpect(
                        jsonPath(
                                operationPath + ".responses['409'].content['application/json'].schema.oneOf[*]['$ref']"
                        ).value(
                                containsInAnyOrder(
                                        START_RESPONSE_REF,
                                        ERROR_RESPONSE_REF
                                )
                        )
                );
    }
}
