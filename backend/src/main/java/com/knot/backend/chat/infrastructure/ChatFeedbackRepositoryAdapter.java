package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.domain.ChatFeedback;
import com.knot.backend.chat.domain.ChatFeedbackRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatFeedbackRepositoryAdapter implements ChatFeedbackRepository {
    private final ChatFeedbackJpaRepository chatFeedbackJpaRepository;

    @Override
    public ChatFeedback save(ChatFeedback chatFeedback) {
        return chatFeedbackJpaRepository.save(chatFeedback);
    }

    @Override
    public Optional<ChatFeedback> findByMessageIdAndMemberId(
            long messageId,
            long memberId
    ) {
        return chatFeedbackJpaRepository.findByMessageIdAndMemberId(
                messageId,
                memberId
        );
    }
}
