package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.domain.ChatSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChatSessionJpaRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findAllByWorkspaceIdAndMemberIdOrderByLastMessageAtDescIdDesc(
            long workspaceId,
            long memberId
    );
}
