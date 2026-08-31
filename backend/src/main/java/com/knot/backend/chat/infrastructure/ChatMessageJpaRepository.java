package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.domain.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChatMessageJpaRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findAllBySessionIdOrderByCreatedAtAscIdAsc(long sessionId);
}
