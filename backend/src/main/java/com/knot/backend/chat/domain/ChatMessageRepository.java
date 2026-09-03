package com.knot.backend.chat.domain;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository {

    ChatMessage save(ChatMessage chatMessage);

    Optional<ChatMessage> findById(long chatMessageId);

    List<ChatMessage> findAllBySessionId(long sessionId);
}
