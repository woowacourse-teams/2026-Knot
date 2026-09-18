package com.knot.backend.chat.domain;

import com.knot.backend.global.exception.ProjectException;

public final class ChatException extends ProjectException {

    public ChatException(ChatErrorCode errorCode) {
        super(errorCode);
    }

    public ChatException(
            ChatErrorCode errorCode,
            Throwable cause
    ) {
        super(
                errorCode,
                cause
        );
    }
}
