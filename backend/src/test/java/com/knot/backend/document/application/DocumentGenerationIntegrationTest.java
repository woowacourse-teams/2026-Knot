package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.infrastructure.llm.DocumentGenerationPrompt;
import com.knot.backend.document.infrastructure.llm.LlmDocumentGenerator;
import com.knot.backend.document.infrastructure.llm.DocumentMarkdownValidator;
import com.knot.backend.global.config.LlmProperties;
import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.infrastructure.llm.LmStudioClient;
import com.knot.backend.testsupport.LlmHttpServer;
import java.net.http.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Tag("integration")
class DocumentGenerationIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("작성 서비스부터 실제 HTTP·JSON·Markdown 검증을 거쳐 생성 결과를 반환한다")
    void generate_success_completeFlow() throws Exception {
        // given
        try (LlmHttpServer server = new LlmHttpServer()) {
            String content = "## 핵심 요약\n사용자 수가 1,000명을 넘으면 검색을 개발한다.\n\n## 보류\n- 검색 개발: 사용자 수가 1,000명을 넘으면 진행한다.";
            server.respond(
                    200,
                    envelope(
                            "stop",
                            mapper.writeValueAsString(
                                    mapper.createObjectNode()
                                            .put(
                                                    "title",
                                                    "검색 도입 조건"
                                            )
                                            .putNull("summary")
                                            .put(
                                                    "content",
                                                    content
                                            )
                            )
                    )
            );
            String transcript = "민지: 사용자 수가 1,000명을 넘으면 검색을 만들자.\n준호: 동의해요. 알림은 다른 안건이에요.";

            // when
            DocumentGenerationResult result = service(server).generate(
                    transcript,
                    "검색 도입 조건"
            );

            // then
            assertThat(result.title()).isEqualTo("검색 도입 조건");
            assertThat(result.summary()).isNull();
            assertThat(result.content()).isEqualTo(content);
            assertThat(server.requestCount()).isEqualTo(1);
            assertThat(server.authorization()).isEqualTo("Bearer test-secret");
            JsonNode request = mapper.readTree(server.requestBody());
            JsonNode input = mapper.readTree(
                    request.path("messages")
                            .get(1)
                            .path("content")
                            .asString()
            );
            assertThat(
                    input.path("transcriptText")
                            .asString()
            ).isEqualTo(transcript);
            assertThat(
                    input.path("topic")
                            .asString()
            ).isEqualTo("검색 도입 조건");
            assertThat(
                    request.path("response_format")
                            .path("json_schema")
                            .path("strict")
                            .asBoolean()
            ).isTrue();
            assertThat(
                    request.path("stream")
                            .asBoolean()
            ).isFalse();
            assertThat(
                    request.path("reasoning")
                            .asString()
            ).isEqualTo("off");
            assertThat(request.has("reasoning_budget")).isFalse();
            assertThat(request.has("thinking_budget_tokens")).isFalse();
            assertThat(
                    request.path("max_tokens")
                            .asInt()
            ).isEqualTo(4096);
        }
    }

    @Test
    @DisplayName("공급자가 반환한 잘못된 Markdown은 상위 유즈케이스에서 거절한다")
    void generate_failure_invalidMarkdown() throws Exception {
        // given
        try (LlmHttpServer server = new LlmHttpServer()) {
            server.respond(
                    200,
                    envelope(
                            "stop",
                            "{\"title\":\"제목\",\"summary\":null,\"content\":\"## 핵심 요약\\n요약\\n## 결정\\n없음\"}"
                    )
            );

            // when & then
            assertThatThrownBy(
                    () -> service(server).generate(
                            "검색하자",
                            "검색"
                    )
            ).hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE.getMessage());
            assertThat(server.requestCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("출력 한도에 도달한 본문은 JSON이 있어도 성공으로 반환하지 않는다")
    void generate_failure_outputLimit() throws Exception {
        // given
        try (LlmHttpServer server = new LlmHttpServer()) {
            server.respond(
                    200,
                    envelope(
                            "length",
                            "{\"title\":\"제목\",\"summary\":null,\"content\":\"## 핵심 요약\\n내용\"}"
                    )
            );

            // when & then
            assertThatThrownBy(
                    () -> service(server).generate(
                            "검색하자",
                            "검색"
                    )
            ).hasMessage(LlmErrorCode.LLM_OUTPUT_LIMIT_EXCEEDED.getMessage());
            assertThat(server.requestCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("공급자 실패를 숨기거나 자동 재호출하지 않는다")
    void generate_failure_providerError() throws Exception {
        // given
        try (LlmHttpServer server = new LlmHttpServer()) {
            server.respond(
                    429,
                    "{\"error\":\"rate limited\"}"
            );

            // when & then
            assertThatThrownBy(
                    () -> service(server).generate(
                            "검색하자",
                            "검색"
                    )
            ).hasMessage(LlmErrorCode.LLM_RATE_LIMITED.getMessage());
            assertThat(server.requestCount()).isEqualTo(1);
        }
    }

    private String envelope(
            String finishReason,
            String content
    ) {
        ObjectNode envelope = mapper.createObjectNode();
        ObjectNode choice = envelope.putArray("choices")
                .addObject();
        choice.put(
                "finish_reason",
                finishReason
        );
        choice.putObject("message")
                .put(
                        "content",
                        content
                );
        return mapper.writeValueAsString(envelope);
    }

    private DocumentGenerationService service(LlmHttpServer server) {
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
        LlmDocumentGenerator generator = new LlmDocumentGenerator(
                new DocumentGenerationPrompt(mapper),
                client,
                mapper,
                new DocumentMarkdownValidator()
        );
        return new DocumentGenerationService(generator);
    }
}
