package com.knot.backend.chat.application;

import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatMessageRole;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessagePersistenceService {
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatMessage saveMessage(
            long sessionId,
            ChatMessageRole role,
            String content,
            Instant createdAt
    ) {
        ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_SESSION_NOT_FOUND));
        ChatMessage chatMessage = ChatMessage.create(
                sessionId,
                role,
                content,
                createdAt
        );
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        chatSession.updateLastMessageAt(createdAt);
        chatSessionRepository.save(chatSession);
        return savedMessage;
    }
}
