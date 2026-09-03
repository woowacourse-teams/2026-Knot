package com.knot.backend.chat.application;

import com.knot.backend.chat.domain.ChatErrorCode;

public interface ChatStreamListener {

    boolean onChunk(String delta);

    boolean onComplete(long messageId);

    void onError(ChatErrorCode errorCode);
}
