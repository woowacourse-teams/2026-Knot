package com.knot.backend.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.ChatMessageService;
import com.knot.backend.chat.application.ChatStreamHandle;
import com.knot.backend.chat.presentation.dto.request.SendChatMessageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ChatMessageControllerTest {

    @Test
    @DisplayName("메시지 전송 요청은 SSE emitter를 반환한다")
    void sendMessage_success() {
        // given
        ChatMessageService service = mock(ChatMessageService.class);
        ChatMessageController controller = new ChatMessageController(service);
        when(
                service.sendMessage(
                        anyLong(),
                        anyLong(),
                        any(),
                        any()
                )
        ).thenReturn(new ChatStreamHandle(() -> {
        }));

        // when
        SseEmitter result = controller.sendMessage(
                10L,
                new SendChatMessageRequest("질문"),
                AuthenticatedMember.of(
                        2L,
                        "흑곰",
                        null
                )
        );

        // then
        assertThat(result).isNotNull();
    }
}
