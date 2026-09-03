package com.knot.backend.chat.application;

import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatMessageRole;
import com.knot.backend.search.domain.SearchReference;
import com.knot.backend.search.domain.SearchReferenceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageSourceQueryService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionAccessPolicy chatSessionAccessPolicy;
    private final SearchReferenceRepository searchReferenceRepository;

    @Transactional(readOnly = true)
    public List<SearchReference> findSources(
            long messageId,
            long memberId
    ) {
        validateMessageId(messageId);
        ChatMessage chatMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND));
        chatSessionAccessPolicy.requireOwner(
                chatMessage.getSessionId(),
                memberId
        );
        requireAssistantMessage(chatMessage);
        return searchReferenceRepository.findAllByMessageId(messageId);
    }

    private void validateMessageId(long messageId) {
        if (messageId <= 0) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_MESSAGE_ID);
        }
    }

    private void requireAssistantMessage(ChatMessage chatMessage) {
        if (chatMessage.getRole() != ChatMessageRole.ASSISTANT) {
            throw new ChatException(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND);
        }
    }
}
