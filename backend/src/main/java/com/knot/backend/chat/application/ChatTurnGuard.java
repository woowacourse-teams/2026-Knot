package com.knot.backend.chat.application;

import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRole;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 세션당 진행 중 턴 1개 규칙(데스크톱 로드맵 Q23). 세션의 마지막 메시지가 답변 없는 USER이고 생성 시각이 timeout 안이면 새
 * 검색·저장을 거절한다. timeout이 지난 미완 턴은 그대로 남기고 다음 요청을 받는다. 검색 API(S1)와 턴 저장 API(S2)가
 * 공유한다.
 */
public final class ChatTurnGuard {

    private ChatTurnGuard() {}

    public static void requireNoTurnInProgress(
            List<ChatMessage> history,
            Instant now,
            Duration turnTimeout
    ) {
        if (history.isEmpty()) {
            return;
        }
        ChatMessage lastMessage = history.getLast();
        if (lastMessage.getRole() != ChatMessageRole.USER) {
            return;
        }
        Instant expiresAt = lastMessage.getCreatedAt()
                .plus(turnTimeout);
        if (now.isBefore(expiresAt)) {
            throw new ChatException(ChatErrorCode.CHAT_TURN_IN_PROGRESS);
        }
    }
}
