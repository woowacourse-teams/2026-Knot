package com.knot.backend.chat.infrastructure.anthropic;

import com.knot.backend.chat.application.dto.command.LlmMessage;
import com.knot.backend.chat.application.dto.command.LlmMessageRole;
import com.knot.backend.chat.application.dto.command.LlmRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class AnthropicRequestMapper {
    private static final String BLOCK_SEPARATOR = "\n\n";

    private final AnthropicLlmProperties properties;

    AnthropicRequestMapper(AnthropicLlmProperties properties) {
        this.properties = properties;
    }

    Map<String, Object> toPayload(LlmRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "model",
                properties.model()
        );
        payload.put(
                "max_tokens",
                properties.maxTokens()
        );
        payload.put(
                "stream",
                true
        );
        String system = systemPrompt(request.messages());
        if (!system.isEmpty()) {
            payload.put(
                    "system",
                    system
            );
        }
        payload.put(
                "messages",
                conversation(request.messages())
        );
        // temperature는 보내지 않는다. thinking은 생략해 모델 기본값(Opus 5는 adaptive)을 따른다.
        payload.put(
                "output_config",
                Map.of(
                        "effort",
                        properties.effortLevel()
                )
        );
        return payload;
    }

    // SYSTEM 메시지는 messages 배열에 들어갈 수 없어 system 필드 하나로 합친다.
    private String systemPrompt(List<LlmMessage> messages) {
        return messages.stream()
                .filter(message -> message.role() == LlmMessageRole.SYSTEM)
                .map(LlmMessage::content)
                .collect(Collectors.joining(BLOCK_SEPARATOR));
    }

    // 같은 역할이 연속되면 한 turn으로 합친다. Messages API는 user/assistant가 번갈아 오는 대화를 기대한다.
    private List<Map<String, String>> conversation(List<LlmMessage> messages) {
        List<Map<String, String>> turns = new ArrayList<>();
        String currentRole = null;
        StringBuilder currentContent = new StringBuilder();
        for (LlmMessage message : messages) {
            if (message.role() == LlmMessageRole.SYSTEM) {
                continue;
            }
            String role = message.role()
                    .name()
                    .toLowerCase(Locale.ROOT);
            if (role.equals(currentRole)) {
                currentContent.append(BLOCK_SEPARATOR)
                        .append(message.content());
                continue;
            }
            if (currentRole != null) {
                turns.add(
                        turn(
                                currentRole,
                                currentContent.toString()
                        )
                );
            }
            currentRole = role;
            currentContent = new StringBuilder(message.content());
        }
        if (currentRole != null) {
            turns.add(
                    turn(
                            currentRole,
                            currentContent.toString()
                    )
            );
        }
        return turns;
    }

    private Map<String, String> turn(
            String role,
            String content
    ) {
        return Map.of(
                "role",
                role,
                "content",
                content
        );
    }
}
