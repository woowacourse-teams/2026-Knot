package com.knot.backend.chat.domain;

import java.util.Optional;

public interface ChatFeedbackRepository {

    ChatFeedback save(ChatFeedback chatFeedback);

    Optional<ChatFeedback> findByMessageIdAndMemberId(
            long messageId,
            long memberId
    );
}
