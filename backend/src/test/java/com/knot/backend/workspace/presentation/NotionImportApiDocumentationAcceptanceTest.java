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
                        jsonPath(
                                operationPath + ".responses['400'].content['application/json']"
                                        + ".examples.invalidNotionImportRunId.value.code"
                        ).value("INVALID_NOTION_IMPORT_RUN_ID")
                )
                .andExpect(
                        jsonPath(
                                operationPath + ".responses['400'].content['application/json']"
                                        + ".examples.invalidNotionImportRunId.value.message"
                        ).value("Notion Import 실행 ID가 올바르지 않습니다")
                )
                .andExpect(
                        jsonPath(
                                operationPath + ".responses['400'].content['application/json']"
                                        + ".examples.invalidParameter.value.code"
                        ).value("INVALID_PARAMETER")
                )
                .andExpect(
                        jsonPath(
                                operationPath + ".responses['400'].content['application/json']"
                                        + ".examples.invalidParameter.value.message"
                        ).value("요청 파라미터 형식이 올바르지 않습니다")
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(
                                operationPath + ".responses['401'].content['application/json']"
                                        + ".examples.unauthenticated.value.code"
                        ).value("UNAUTHENTICATED")
                )
                .andExpect(
                        jsonPath(
                                operationPath + ".responses['401'].content['application/json']"
                                        + ".examples.unauthenticated.value.message"
                        ).value("인증이 필요합니다")
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(ERROR_RESPONSE_REF)
                )
                .andExpect(
                        jsonPath(
                                operationPath + ".responses['404'].content['application/json']"
                                        + ".examples.notionImportRunNotFound.value.code"
                        ).value("NOTION_IMPORT_RUN_NOT_FOUND")
                )
                .andExpect(
                        jsonPath(
                                operationPath + ".responses['404'].content['application/json']"
                                        + ".examples.notionImportRunNotFound.value.message"
                        ).value("Notion Import 실행을 찾을 수 없습니다")
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
}
