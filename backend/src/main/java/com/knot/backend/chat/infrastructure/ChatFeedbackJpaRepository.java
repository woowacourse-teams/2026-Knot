package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.domain.ChatFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChatFeedbackJpaRepository extends JpaRepository<ChatFeedback, Long> {

    Optional<ChatFeedback> findByMessageIdAndMemberId(
            long messageId,
            long memberId
    );
}
