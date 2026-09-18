package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryAdapter implements ChatMessageRepository {
    private final ChatMessageJpaRepository chatMessageJpaRepository;

    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        return chatMessageJpaRepository.save(chatMessage);
    }

    @Override
    public Optional<ChatMessage> findById(long chatMessageId) {
        return chatMessageJpaRepository.findById(chatMessageId);
    }

    @Override
    public List<ChatMessage> findAllBySessionId(long sessionId) {
        return chatMessageJpaRepository.findAllBySessionIdOrderByCreatedAtAscIdAsc(sessionId);
    }
}
