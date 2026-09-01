package com.knot.backend.chat.presentation;

import com.knot.backend.chat.application.ChatStreamListener;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.presentation.dto.response.ChatChunkEvent;
import com.knot.backend.chat.presentation.dto.response.ChatCompleteEvent;
import com.knot.backend.chat.presentation.dto.response.ChatErrorEvent;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequiredArgsConstructor
public class ChatSseStreamListener implements ChatStreamListener {
    private final SseEmitter emitter;

    @Override
    public boolean onChunk(String delta) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("chunk")
                            .data(new ChatChunkEvent(delta))
            );
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public boolean onComplete(long messageId) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("complete")
                            .data(new ChatCompleteEvent(messageId))
            );
            emitter.complete();
            return true;
        } catch (IOException exception) {
            emitter.completeWithError(exception);
            return false;
        }
    }

    @Override
    public void onError(ChatErrorCode errorCode) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("error")
                            .data(
                                    new ChatErrorEvent(
                                            errorCode.getCode(),
                                            errorCode.getMessage()
                                    )
                            )
            );
        } catch (IOException ignored) {
            // 연결이 이미 종료된 경우에는 추가 응답을 보낼 수 없다.
        } finally {
            emitter.complete();
        }
    }
}
