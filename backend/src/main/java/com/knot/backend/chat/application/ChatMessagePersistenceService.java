package com.knot.backend.chat.application;

import com.knot.backend.chat.application.dto.result.ChatTurn;
import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageGeneratedBy;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatMessageRole;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import com.knot.backend.chat.domain.LlmUsage;
import com.knot.backend.search.application.SearchReferencePersistenceService;
import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchReferenceCandidate;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessagePersistenceService {
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SearchReferencePersistenceService searchReferencePersistenceService;

    @Transactional
    public ChatMessage saveMessage(
            long sessionId,
            ChatMessageRole role,
            String content,
            Instant createdAt
    ) {
        return saveMessageInternal(
                sessionId,
                role,
                content,
                createdAt
        );
    }

    /**
     * 서버가 만든 답변을 근거·토큰 사용량과 함께 한 트랜잭션에 저장한다(데스크톱 기획서 6.2 계측 행, 로드맵 B2).
     * 사용량을 알려 주지 않는 어댑터와 안내 문구 폴백은 {@code usage}가 null이며 컬럼이 전부 NULL로 남는다.
     */
    @Transactional
    public ChatMessage saveAssistantWithReferences(
            long sessionId,
            String content,
            Instant createdAt,
            List<SearchChunk> references,
            LlmUsage usage
    ) {
        ChatSession chatSession = findSession(sessionId);
        ChatMessage savedMessage = chatMessageRepository.save(
                ChatMessage.createServerAnswer(
                        sessionId,
                        content,
                        createdAt,
                        usage
                )
        );
        touchSession(
                chatSession,
                createdAt
        );
        searchReferencePersistenceService.replace(
                savedMessage.getId(),
                references
        );
        return savedMessage;
    }

    /**
     * 검색 API가 READY가 아닐 때 USER 질문과 서버 안내 문구(ASSISTANT)를 같은 트랜잭션에 저장한다(기획서 6.4, 로드맵
     * Q30). 안내 답변은 서버가 만들었으므로 generated_by는 SERVER이고 근거는 없다.
     */
    @Transactional
    public ChatTurn saveFallbackTurn(
            long sessionId,
            String question,
            String fallbackAnswer,
            Instant createdAt
    ) {
        ChatMessage userMessage = saveMessageInternal(
                sessionId,
                ChatMessageRole.USER,
                question,
                createdAt
        );
        ChatMessage assistantMessage = saveMessageInternal(
                sessionId,
                ChatMessageRole.ASSISTANT,
                fallbackAnswer,
                createdAt
        );
        return new ChatTurn(
                userMessage,
                assistantMessage
        );
    }

    /**
     * CLI 에이전트가 만든 질문·답변·근거를 한 트랜잭션에 저장한다(기획서 6.4 턴 저장 API, 로드맵 S2). 답변은 클라이언트가
     * 만들었으므로 generated_by가 CLIENT이고, 근거는 Workspace JOIN으로만 검증된다(로드맵 Q24·Q25). 근거 검증에 실패하면
     * 질문·답변도 함께 되돌린다.
     */
    @Transactional
    public ChatTurn saveClientTurn(
            long sessionId,
            String question,
            String answer,
            Instant createdAt,
            List<SearchReferenceCandidate> references
    ) {
        ChatMessage userMessage = saveMessageInternal(
                sessionId,
                ChatMessageRole.USER,
                question,
                createdAt
        );
        ChatMessage assistantMessage = saveMessageInternal(
                sessionId,
                ChatMessageRole.ASSISTANT,
                answer,
                createdAt,
                ChatMessageGeneratedBy.CLIENT
        );
        searchReferencePersistenceService.replaceCandidates(
                assistantMessage.getId(),
                references
        );
        return new ChatTurn(
                userMessage,
                assistantMessage
        );
    }

    private ChatMessage saveMessageInternal(
            long sessionId,
            ChatMessageRole role,
            String content,
            Instant createdAt
    ) {
        return saveMessageInternal(
                sessionId,
                role,
                content,
                createdAt,
                ChatMessageGeneratedBy.SERVER
        );
    }

    private ChatMessage saveMessageInternal(
            long sessionId,
            ChatMessageRole role,
            String content,
            Instant createdAt,
            ChatMessageGeneratedBy generatedBy
    ) {
        ChatSession chatSession = findSession(sessionId);
        ChatMessage chatMessage = ChatMessage.create(
                sessionId,
                role,
                content,
                createdAt,
                generatedBy
        );
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        touchSession(
                chatSession,
                createdAt
        );
        return savedMessage;
    }

    private ChatSession findSession(long sessionId) {
        return chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_SESSION_NOT_FOUND));
    }

    private void touchSession(
            ChatSession chatSession,
            Instant createdAt
    ) {
        chatSession.updateLastMessageAt(createdAt);
        chatSessionRepository.save(chatSession);
    }
}
