package com.knot.backend.chat.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.ChatMessageService;
import com.knot.backend.chat.application.ChatStreamHandle;
import com.knot.backend.chat.application.ChatStreamListener;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.presentation.dto.request.SendChatMessageRequest;
import com.knot.backend.global.config.ChatStreamingProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ChatMessageController {
    private final ChatMessageService chatMessageService;
    private final ChatStreamingProperties chatStreamingProperties;

    @PostMapping(path = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @PathVariable long sessionId,
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        SseEmitter emitter = new SseEmitter(chatStreamingProperties.timeoutMillis());
        ChatStreamListener listener = new ChatSseStreamListener(emitter);
        ChatStreamHandle handle = chatMessageService.sendMessage(
                sessionId,
                authenticatedMember.getMemberId(),
                request.content(),
                listener
        );
        emitter.onCompletion(handle::cancel);
        emitter.onError(error -> handle.cancel());
        emitter.onTimeout(() -> {
            if (handle.cancel()) {
                listener.onError(ChatErrorCode.LLM_STREAM_TIMEOUT);
            }
        });
        return emitter;
    }
}
