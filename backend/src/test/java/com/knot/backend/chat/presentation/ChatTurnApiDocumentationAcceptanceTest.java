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
class ChatTurnApiDocumentationAcceptanceTest {
    private final MockMvc mockMvc;

    ChatTurnApiDocumentationAcceptanceTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("OpenAPI JSON에 턴 저장의 요청·응답·보안·오류 계약을 공개한다")
    void openApi_success_chatTurnContract() throws Exception {
        // given
        String operationPath = "$.paths['/api/v1/conversations/{sessionId}/turns'].post";
        String requestRef = "#/components/schemas/SaveChatTurnRequest";
        String responseRef = "#/components/schemas/ChatTurnResponse";
        String errorResponseRef = "#/components/schemas/ErrorResponse";

        // when
        ResultActions result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath(operationPath).exists())
                .andExpect(jsonPath(operationPath + ".summary").value("턴 저장"))
                .andExpect(jsonPath(operationPath + ".security[0].bearerAuth").exists())
                .andExpect(
                        jsonPath(operationPath + ".requestBody.content['application/json'].schema['$ref']")
                                .value(requestRef)
                )
                .andExpect(
                        jsonPath(operationPath + ".responses['201'].content['application/json'].schema['$ref']")
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
                .andExpect(jsonPath("$.components.schemas.SaveChatTurnRequest.properties.question").exists())
                .andExpect(jsonPath("$.components.schemas.SaveChatTurnRequest.properties.answer").exists())
                .andExpect(jsonPath("$.components.schemas.SaveChatTurnRequest.properties.references").exists())
                .andExpect(jsonPath("$.components.schemas.SaveChatTurnRequest.properties.references.maxItems").value(8))
                .andExpect(jsonPath("$.components.schemas.SaveChatTurnReferenceRequest.properties.importRunId").exists())
                .andExpect(jsonPath("$.components.schemas.SaveChatTurnReferenceRequest.properties.importedPageId").exists())
                .andExpect(jsonPath("$.components.schemas.SaveChatTurnReferenceRequest.properties.chunkIndex").exists())
                .andExpect(jsonPath("$.components.schemas.SaveChatTurnReferenceRequest.properties.score").exists())
                .andExpect(jsonPath("$.components.schemas.ChatTurnResponse.properties.userMessageId").exists())
                .andExpect(jsonPath("$.components.schemas.ChatTurnResponse.properties.messageId").exists())
                .andExpect(jsonPath("$.components.schemas.ChatTurnResponse.properties.sources").doesNotExist());
    }
}
