package com.knot.backend.chat.application;

import com.knot.backend.chat.application.dto.command.CreateChatSessionCommand;
import com.knot.backend.chat.application.dto.result.ChatMessageResult;
import com.knot.backend.chat.application.dto.result.ChatSessionResult;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatSessionService {
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionAccessPolicy chatSessionAccessPolicy;

    @Transactional
    public ChatSessionResult createSession(CreateChatSessionCommand command) {
        chatSessionAccessPolicy.requireWorkspaceMember(
                command.workspaceId(),
                command.memberId()
        );
        ChatSession chatSession = ChatSession.create(
                command.workspaceId(),
                command.memberId(),
                command.title(),
                Instant.now()
        );
        return ChatSessionResult.from(chatSessionRepository.save(chatSession));
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResult> findSessions(
            long workspaceId,
            long memberId
    ) {
        chatSessionAccessPolicy.requireWorkspaceMember(
                workspaceId,
                memberId
        );
        return chatSessionRepository.findAllByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )
                .stream()
                .map(ChatSessionResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResult> findMessages(
            long sessionId,
            long memberId
    ) {
        chatSessionAccessPolicy.requireOwner(
                sessionId,
                memberId
        );
        return chatMessageRepository.findAllBySessionId(sessionId)
                .stream()
                .map(ChatMessageResult::from)
                .toList();
    }
}
