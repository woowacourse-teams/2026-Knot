package com.knot.backend.chat.presentation;

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
class ChatMessageSourceQueryApiDocumentationAcceptanceTest {
    private final MockMvc mockMvc;

    ChatMessageSourceQueryApiDocumentationAcceptanceTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("OpenAPI JSON에 메시지 출처 조회의 응답·보안·오류 계약을 공개한다")
    void openApi_success_messageSourcesContract() throws Exception {
        // given
        String operationPath = "$.paths['/api/v1/messages/{messageId}/sources'].get";
        String responseRef = "#/components/schemas/SearchReferencesResponse";
        String errorResponseRef = "#/components/schemas/ErrorResponse";

        // when
        ResultActions result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath(operationPath).exists())
                .andExpect(jsonPath(operationPath + ".summary").value("AI 답변 출처 조회"))
                .andExpect(jsonPath(operationPath + ".security[0].accessTokenCookie").exists())
                .andExpect(
                        jsonPath(operationPath + ".responses['200'].content['application/json'].schema['$ref']")
                                .value(responseRef)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['400'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['401'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['403'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['404'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath("$.components.schemas.SearchReferencesResponse.properties.searchReferences").exists()
                )
                .andExpect(jsonPath("$.components.schemas.SearchReferenceResponse.properties.id").exists())
                .andExpect(jsonPath("$.components.schemas.SearchReferenceResponse.properties.messageId").exists())
                .andExpect(jsonPath("$.components.schemas.SearchReferenceResponse.properties.rank").exists())
                .andExpect(jsonPath("$.components.schemas.SearchReferenceResponse.properties.relevanceScore").exists())
                .andExpect(jsonPath("$.components.schemas.SearchReferenceResponse.properties.source").exists())
                .andExpect(jsonPath("$.components.schemas.SearchReferenceResponse.properties.notionPage").exists())
                .andExpect(jsonPath("$.components.schemas.NotionPageReferenceResponse.properties.id").exists())
                .andExpect(jsonPath("$.components.schemas.NotionPageReferenceResponse.properties.title").exists())
                .andExpect(jsonPath("$.components.schemas.NotionPageReferenceResponse.properties.notionUrl").exists())
                .andExpect(jsonPath("$.components.schemas.NotionPageReferenceResponse.properties.createdAt").exists())
                .andExpect(jsonPath("$.components.schemas.NotionPageReferenceResponse.properties.updatedAt").exists());
    }
}
