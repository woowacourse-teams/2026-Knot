package com.knot.backend.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.chat.application.LlmStream;
import com.knot.backend.chat.application.dto.command.LlmRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeLlmClientTest {

    @Test
    @DisplayName("fake LLM은 응답을 여러 chunk로 나누어 반환한다")
    void start_success_chunks() {
        // given
        FakeLlmClient client = new FakeLlmClient();

        // when
        LlmStream stream = client.start(new LlmRequest(List.of()));

        // then
        assertThat(stream.hasNext()).isTrue();
        assertThat(stream.next()).isEqualTo("테스트 ");
        assertThat(stream.hasNext()).isTrue();
        assertThat(stream.next()).isEqualTo("LLM 응답입니다.");
        assertThat(stream.hasNext()).isFalse();
    }
}
