package com.knot.backend.chat.application;

import com.knot.backend.chat.domain.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * 세션 이력(직전 4개)과 현재 질문으로 검색 질의를 조립한다(기능 기획서 7절 후속 질문 정책). 서버 SSE 경로와 데스크톱용 검색
 * API가 같은 질의를 만들도록 한 곳에 둔다(기획서 6.4, 로드맵 Q21).
 */
public final class ChatSearchQueryComposer {
    static final int MAX_SEARCH_HISTORY_MESSAGES = 4;
    static final int MAX_SEARCH_QUERY_CHARACTERS = 4000;

    private ChatSearchQueryComposer() {}

    /**
     * @param query
     *            현재 질문
     * @param previousMessages
     *            현재 질문보다 앞선 세션 메시지(시간순). 현재 질문은 포함하지 않는다
     */
    public static String compose(
            String query,
            List<ChatMessage> previousMessages
    ) {
        if (previousMessages.isEmpty()) {
            return query;
        }
        int firstMessageIndex = Math.max(
                0,
                previousMessages.size() - MAX_SEARCH_HISTORY_MESSAGES
        );
        List<String> lines = new ArrayList<>();
        for (int index = firstMessageIndex; index < previousMessages.size(); index++) {
            ChatMessage message = previousMessages.get(index);
            lines.add(message.getRole() + ": " + message.getContent());
        }
        String currentQuestion = "현재 질문: " + query;
        String previousContext = String.join(
                "\n",
                lines
        );
        int availableCharacters = MAX_SEARCH_QUERY_CHARACTERS - currentQuestion.length() - 1;
        if (availableCharacters <= 0) {
            return query;
        }
        if (previousContext.length() > availableCharacters) {
            previousContext = previousContext.substring(previousContext.length() - availableCharacters);
        }
        return previousContext + "\n" + currentQuestion;
    }
}
