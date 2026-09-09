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
class WorkspaceSearchApiDocumentationAcceptanceTest {
    private final MockMvc mockMvc;

    WorkspaceSearchApiDocumentationAcceptanceTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("OpenAPI JSON에 Workspace 검색의 요청·응답·보안·오류 계약을 공개한다")
    void openApi_success_workspaceSearchContract() throws Exception {
        // given
        String operationPath = "$.paths['/api/v1/workspaces/{workspaceId}/search'].post";
        String requestRef = "#/components/schemas/WorkspaceSearchRequest";
        String responseRef = "#/components/schemas/WorkspaceSearchResponse";
        String errorResponseRef = "#/components/schemas/ErrorResponse";

        // when
        ResultActions result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath(operationPath).exists())
                .andExpect(jsonPath(operationPath + ".summary").value("Workspace 검색"))
                .andExpect(jsonPath(operationPath + ".security[0].bearerAuth").exists())
                .andExpect(
                        jsonPath(operationPath + ".requestBody.content['application/json'].schema['$ref']")
                                .value(requestRef)
                )
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
                        jsonPath(operationPath + ".responses['409'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['500'].content['application/json'].schema['$ref']")
                                .value(errorResponseRef)
                )
                .andExpect(jsonPath("$.components.schemas.WorkspaceSearchResponse.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.WorkspaceSearchResponse.properties.groundingRules").exists())
                .andExpect(jsonPath("$.components.schemas.WorkspaceSearchResponse.properties.chunks").exists())
                .andExpect(jsonPath("$.components.schemas.WorkspaceSearchResponse.properties.fallbackAnswer").exists())
                .andExpect(
                        jsonPath("$.components.schemas.WorkspaceSearchResponse.properties.userMessageId").doesNotExist()
                )
                .andExpect(jsonPath("$.components.schemas.ChatSearchChunkResponse.properties.chunkIndex").exists());
    }
}
