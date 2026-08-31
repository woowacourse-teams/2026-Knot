package com.knot.backend.chat.domain;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository {

    ChatSession save(ChatSession chatSession);

    Optional<ChatSession> findById(long chatSessionId);

    List<ChatSession> findAllByWorkspaceIdAndMemberId(
            long workspaceId,
            long memberId
    );
}
