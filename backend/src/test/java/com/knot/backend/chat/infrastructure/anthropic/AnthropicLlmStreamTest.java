package com.knot.backend.chat.infrastructure.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AnthropicLlmStreamTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("text_delta만 chunk로 돌려주고 thinking 델타·ping·블록 경계 이벤트는 건너뛴 뒤 message_stop에서 끝난다")
    void hasNext_success_returnsOnlyTextDeltas() {
        // given
        AnthropicLlmStream stream = stream(
                """
                        event: message_start
                        data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5","content":[],"usage":{"input_tokens":25,"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":1}}}

                        event: content_block_start
                        data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}

                        event: content_block_delta
                        data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"생각 중"}}

                        event: content_block_delta
                        data: {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig"}}

                        event: content_block_stop
                        data: {"type":"content_block_stop","index":0}

                        event: ping
                        data: {"type":"ping"}

                        event: content_block_start
                        data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}

                        event: content_block_delta
                        data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"첫 "}}

                        event: content_block_delta
                        data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"응답"}}

                        event: content_block_stop
                        data: {"type":"content_block_stop","index":1}

                        event: message_delta
                        data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":12}}

                        event: message_stop
                        data: {"type":"message_stop"}

                        """
        );

        // when & then
        assertThat(stream.next()).isEqualTo("첫 ");
        assertThat(stream.next()).isEqualTo("응답");
        assertThat(stream.hasNext()).isFalse();
        assertThat(stream.hasNext()).isFalse();
    }

    @Test
    @DisplayName("max_tokens로 잘린 응답은 오류가 아니라 정상 종료로 처리한다")
    void hasNext_success_maxTokensStopReasonCompletesNormally() {
        // given
        AnthropicLlmStream stream = stream(
                """
                        data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"부분"}}

                        data: {"type":"message_delta","delta":{"stop_reason":"max_tokens","stop_sequence":null},"usage":{"output_tokens":4096}}

                        data: {"type":"message_stop"}

                        """
        );

        // when & then
        assertThat(stream.next()).isEqualTo("부분");
        assertThat(stream.hasNext()).isFalse();
    }

    @Test
    @DisplayName("스트림 도중 overloaded_error 이벤트가 오면 LLM_RATE_LIMITED로 실패한다")
    void hasNext_failure_overloadedErrorEvent() {
        // given
        AnthropicLlmStream stream = stream("""
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"부분 "}}

                event: error
                data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}

                """);

        // when & then
        assertThat(stream.next()).isEqualTo("부분 ");
        assertThatThrownBy(stream::hasNext).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.chatErrorCode()).isEqualTo(ChatErrorCode.LLM_RATE_LIMITED)
        );
        assertThat(stream.hasNext()).isFalse();
    }

    @Test
    @DisplayName("스트림 도중 authentication_error 이벤트가 오면 LLM_CONFIGURATION_INVALID로 실패한다")
    void hasNext_failure_authenticationErrorEvent() {
        // given
        AnthropicLlmStream stream = stream("""
                data: {"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}

                """);

        // when & then
        assertThatThrownBy(stream::hasNext).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.chatErrorCode()).isEqualTo(ChatErrorCode.LLM_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("stop_reason이 refusal이면 LLM_REFUSED로 실패한다")
    void hasNext_failure_refusalStopReason() {
        // given
        AnthropicLlmStream stream = stream(
                """
                        data: {"type":"message_delta","delta":{"stop_reason":"refusal","stop_sequence":null,"stop_details":{"type":"refusal","category":"cyber"}},"usage":{"output_tokens":0}}

                        data: {"type":"message_stop"}

                        """
        );

        // when & then
        assertThatThrownBy(stream::hasNext).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.chatErrorCode()).isEqualTo(ChatErrorCode.LLM_REFUSED)
        );
    }

    @Test
    @DisplayName("message_stop 없이 스트림이 끝나면 partial chunk를 정상 완료로 처리하지 않는다")
    void hasNext_failure_eofBeforeMessageStop() {
        // given
        AnthropicLlmStream stream = stream("""
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"부분 "}}

                """);

        // when & then
        assertThat(stream.next()).isEqualTo("부분 ");
        assertThatThrownBy(stream::hasNext).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.chatErrorCode()).isEqualTo(ChatErrorCode.LLM_STREAM_FAILED)
        );
    }

    @Test
    @DisplayName("data가 JSON이 아니면 LLM_STREAM_FAILED로 실패한다")
    void hasNext_failure_malformedData() {
        // given
        AnthropicLlmStream stream = stream("data: not-json\n\n");

        // when & then
        assertThatThrownBy(stream::hasNext).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.chatErrorCode()).isEqualTo(ChatErrorCode.LLM_STREAM_FAILED)
        );
    }

    @Test
    @DisplayName("message_start의 입력·캐시 토큰과 message_delta의 출력 토큰을 사용량으로 올린다")
    void usage_success_collectsInputAndOutputTokens() {
        // given
        AnthropicLlmStream stream = stream(
                """
                        data: {"type":"message_start","message":{"id":"msg_1","model":"claude-opus-5","usage":{"input_tokens":25,"cache_creation_input_tokens":7,"cache_read_input_tokens":9,"output_tokens":1}}}

                        data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"응답"}}

                        data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":12}}

                        data: {"type":"message_stop"}

                        """
        );

        // when
        stream.next();
        boolean hasNext = stream.hasNext();

        // then
        assertThat(hasNext).isFalse();
        assertThat(stream.usage()).hasValueSatisfying(usage -> {
            assertThat(usage.getModel()).isEqualTo("claude-opus-5");
            assertThat(usage.getInputTokens()).isEqualTo(25L);
            assertThat(usage.getCacheCreationInputTokens()).isEqualTo(7L);
            assertThat(usage.getCacheReadInputTokens()).isEqualTo(9L);
            assertThat(usage.getOutputTokens()).isEqualTo(12L);
        });
    }

    @Test
    @DisplayName("message_start를 받기 전에 끝난 스트림에는 사용량이 없다")
    void usage_success_emptyWithoutMessageStart() {
        // given
        AnthropicLlmStream stream = stream(
                """
                        data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"응답"}}

                        data: {"type":"message_stop"}

                        """
        );

        // when
        stream.next();

        // then
        assertThat(stream.usage()).isEmpty();
    }

    @Test
    @DisplayName("message_delta가 오기 전이면 출력 토큰이 0인 사용량을 돌려준다")
    void usage_success_zeroOutputTokensBeforeMessageDelta() {
        // given
        AnthropicLlmStream stream = stream(
                """
                        data: {"type":"message_start","message":{"id":"msg_1","model":"claude-opus-5","usage":{"input_tokens":25}}}

                        data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"응답"}}

                        data: {"type":"message_stop"}

                        """
        );

        // when
        stream.next();

        // then
        assertThat(stream.usage()).hasValueSatisfying(usage -> {
            assertThat(usage.getInputTokens()).isEqualTo(25L);
            assertThat(usage.getOutputTokens()).isZero();
        });
    }

    private AnthropicLlmStream stream(String body) {
        return new AnthropicLlmStream(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                objectMapper
        );
    }
}
