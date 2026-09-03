package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.application.LlmClient;
import com.knot.backend.chat.application.LlmStream;
import com.knot.backend.chat.application.dto.command.LlmRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeLlmClient implements LlmClient {

    @Override
    public LlmStream start(LlmRequest request) {
        return new FakeLlmStream(
                List.of(
                        "테스트 ",
                        "LLM 응답입니다."
                )
        );
    }
}
