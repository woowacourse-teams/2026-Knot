package com.knot.backend.chat.infrastructure.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.chat.application.dto.command.LlmMessage;
import com.knot.backend.chat.application.dto.command.LlmMessageRole;
import com.knot.backend.chat.application.dto.command.LlmRequest;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnthropicRequestMapperTest {
    private final AnthropicRequestMapper mapper = new AnthropicRequestMapper(
            new AnthropicLlmProperties(
                    URI.create("https://api.anthropic.com"),
                    "test-key",
                    "claude-opus-5",
                    "MEDIUM",
                    4096,
                    Duration.ofSeconds(30)
            )
    );

    @Test
    @DisplayName("SYSTEM 메시지는 system 필드로, 나머지는 소문자 역할의 messages로 옮기고 effort는 소문자로 보낸다")
    void toPayload_success_mapsSystemAndConversation() {
        // given
        LlmRequest request = new LlmRequest(
                List.of(
                        new LlmMessage(
                                LlmMessageRole.SYSTEM,
                                "근거 규칙"
                        ),
                        new LlmMessage(
                                LlmMessageRole.USER,
                                "첫 질문"
                        ),
                        new LlmMessage(
                                LlmMessageRole.ASSISTANT,
                                "첫 답변"
                        ),
                        new LlmMessage(
                                LlmMessageRole.USER,
                                "두 번째 질문"
                        )
                )
        );

        // when
        Map<String, Object> payload = mapper.toPayload(request);

        // then
        assertThat(payload).containsEntry(
                "model",
                "claude-opus-5"
        )
                .containsEntry(
                        "max_tokens",
                        4096
                )
                .containsEntry(
                        "stream",
                        true
                )
                .containsEntry(
                        "system",
                        "근거 규칙"
                )
                .containsEntry(
                        "output_config",
                        Map.of(
                                "effort",
                                "medium"
                        )
                )
                .doesNotContainKeys(
                        "temperature",
                        "thinking"
                );
        assertThat(payload.get("messages")).isEqualTo(
                List.of(
                        Map.of(
                                "role",
                                "user",
                                "content",
                                "첫 질문"
                        ),
                        Map.of(
                                "role",
                                "assistant",
                                "content",
                                "첫 답변"
                        ),
                        Map.of(
                                "role",
                                "user",
                                "content",
                                "두 번째 질문"
                        )
                )
        );
    }

    @Test
    @DisplayName("같은 역할이 연속되면 한 turn으로 합치고 SYSTEM 메시지가 여러 개면 system 하나로 합친다")
    void toPayload_success_mergesConsecutiveRoles() {
        // given
        LlmRequest request = new LlmRequest(
                List.of(
                        new LlmMessage(
                                LlmMessageRole.SYSTEM,
                                "규칙 A"
                        ),
                        new LlmMessage(
                                LlmMessageRole.SYSTEM,
                                "규칙 B"
                        ),
                        new LlmMessage(
                                LlmMessageRole.USER,
                                "질문 1"
                        ),
                        new LlmMessage(
                                LlmMessageRole.USER,
                                "질문 2"
                        )
                )
        );

        // when
        Map<String, Object> payload = mapper.toPayload(request);

        // then
        assertThat(payload).containsEntry(
                "system",
                "규칙 A\n\n규칙 B"
        );
        assertThat(payload.get("messages")).isEqualTo(
                List.of(
                        Map.of(
                                "role",
                                "user",
                                "content",
                                "질문 1\n\n질문 2"
                        )
                )
        );
    }

    @Test
    @DisplayName("SYSTEM 메시지가 없으면 system 필드를 보내지 않는다")
    void toPayload_success_omitsEmptySystem() {
        // given
        LlmRequest request = new LlmRequest(
                List.of(
                        new LlmMessage(
                                LlmMessageRole.USER,
                                "질문"
                        )
                )
        );

        // when
        Map<String, Object> payload = mapper.toPayload(request);

        // then
        assertThat(payload).doesNotContainKey("system");
    }
}
