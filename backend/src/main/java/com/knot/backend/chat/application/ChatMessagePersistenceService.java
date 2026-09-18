package com.knot.backend.chat.application;

import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatMessageRole;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import com.knot.backend.search.application.SearchReferencePersistenceService;
import com.knot.backend.search.domain.SearchChunk;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessagePersistenceService {
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SearchReferencePersistenceService searchReferencePersistenceService;

    @Transactional
    public ChatMessage saveMessage(
            long sessionId,
            ChatMessageRole role,
            String content,
            Instant createdAt
    ) {
        return saveMessageInternal(
                sessionId,
                role,
                content,
                createdAt
        );
    }

    @Transactional
    public ChatMessage saveAssistantWithReferences(
            long sessionId,
            String content,
            Instant createdAt,
            List<SearchChunk> references
    ) {
        ChatMessage savedMessage = saveMessageInternal(
                sessionId,
                ChatMessageRole.ASSISTANT,
                content,
                createdAt
        );
        searchReferencePersistenceService.replace(
                savedMessage.getId(),
                references
        );
        return savedMessage;
    }

    private ChatMessage saveMessageInternal(
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
