package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatSessionRepositoryAdapter implements ChatSessionRepository {
    private final ChatSessionJpaRepository chatSessionJpaRepository;

    @Override
    public ChatSession save(ChatSession chatSession) {
        return chatSessionJpaRepository.save(chatSession);
    }

    @Override
    public Optional<ChatSession> findById(long chatSessionId) {
        return chatSessionJpaRepository.findById(chatSessionId);
    }

    @Override
    public List<ChatSession> findAllByWorkspaceIdAndMemberId(
            long workspaceId,
            long memberId
    ) {
        return chatSessionJpaRepository.findAllByWorkspaceIdAndMemberIdOrderByLastMessageAtDescIdDesc(
                workspaceId,
                memberId
        );
    }
}
