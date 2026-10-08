package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.application.dto.result.DocumentTopicClassificationResult;
import com.knot.backend.document.infrastructure.llm.DocumentTopicClassifier;
import com.knot.backend.document.infrastructure.llm.DocumentTopicPrompt;
import com.knot.backend.global.config.LlmProperties;
import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.infrastructure.llm.LmStudioClient;
import com.knot.backend.testsupport.LlmHttpServer;
import java.net.http.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Tag("integration")
class DocumentTopicClassificationIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("상위 호출 진입점부터 HTTP와 정규화를 거쳐 주제 결과를 반환한다")
    void classify_success_completeFlow() throws Exception {
        // given
        try (LlmHttpServer server = new LlmHttpServer()) {
            ObjectNode envelope = mapper.createObjectNode();
            ObjectNode choice = envelope.putArray("choices")
                    .addObject();
            choice.put(
                    "finish_reason",
                    "stop"
            );
            choice.putObject("message")
                    .put(
                            "content",
                            "{\"topics\":[\" 검색　조건 \",\"검색 조건\",\"알림\"]}"
                    );
            server.respond(
                    200,
                    mapper.writeValueAsString(envelope)
            );
            DocumentTopicClassificationService service = service(server);
            String transcript = "민지: 사용자 수가 1,000명을 넘으면 검색을 만들자.\n준호: 알림도 검토하자.";

            // when
            DocumentTopicClassificationResult result = service.classify(transcript);

            // then
            assertThat(result.topics()).containsExactly(
                    "검색 조건",
                    "알림"
            );
            assertThat(result.isNoContent()).isFalse();
            assertThat(server.requestCount()).isEqualTo(1);
            String userMessage = mapper.readTree(server.requestBody())
                    .path("messages")
                    .get(1)
                    .path("content")
                    .asString();
            assertThat(
                    mapper.readTree(userMessage)
                            .path("transcriptText")
                            .asString()
            ).isEqualTo(transcript);
        }
    }

    @Test
    @DisplayName("전체 흐름에서도 실패는 내용 없음으로 바뀌지 않는다")
    void classify_failure_providerError() throws Exception {
        // given
        try (LlmHttpServer server = new LlmHttpServer()) {
            server.respond(
                    429,
                    "{\"error\":\"rate limited\"}"
            );
            DocumentTopicClassificationService service = service(server);

            // when & then
            assertThatThrownBy(() -> service.classify("민지: 검색 개발을 논의합시다."))
                    .hasMessage(LlmErrorCode.LLM_RATE_LIMITED.getMessage());
            assertThat(server.requestCount()).isEqualTo(1);
        }
    }

    private DocumentTopicClassificationService service(LlmHttpServer server) {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(server.baseUrl());
        properties.setApiToken("test-secret");
        properties.setModel("test-model");
        LmStudioClient client = new LmStudioClient(
                HttpClient.newHttpClient(),
                mapper,
                properties
        );
        DocumentTopicClassifier classifier = new DocumentTopicClassifier(
                new DocumentTopicPrompt(mapper),
                client,
                mapper
        );
        return new DocumentTopicClassificationService(classifier);
    }
}
