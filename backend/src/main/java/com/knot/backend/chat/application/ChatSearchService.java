package com.knot.backend.chat.application;

import com.knot.backend.chat.application.dto.result.ChatFallbackTurn;
import com.knot.backend.chat.application.dto.result.ChatSearchResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatMessageRole;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.search.application.PublishedDocumentSearchService;
import com.knot.backend.search.application.SearchContext;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 데스크톱 경유 탐색의 서버 검색 단계(기획서 6.4, 로드맵 S1). 접근·스냅샷·진행 중 턴을 검사하고 하이브리드 검색으로 청크 상위
 * top-k를 고른 뒤 USER 메시지를 저장한다. READY가 아니면 안내 문구를 ASSISTANT로 함께 저장한다. LLM은 부르지
 * 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChatSearchService {
    private final ChatSessionAccessPolicy chatSessionAccessPolicy;
    private final ChatMessagePersistenceService chatMessagePersistenceService;
    private final ChatMessageRepository chatMessageRepository;
    private final PublishedDocumentSearchService documentSearchService;
    private final ActiveChatStreamRegistry activeChatStreamRegistry;
    private final ChatProperties chatProperties;
    private final Clock clock;

    public ChatSearchResult search(
            long sessionId,
            long memberId,
            String content
    ) {
        chatProperties.validate();
        ChatSession session = chatSessionAccessPolicy.requireOwner(
                sessionId,
                memberId
        );
        // SSE 경로와 같은 잠금으로 같은 세션의 동시 진입을 막는다(로드맵 Q23).
        if (!activeChatStreamRegistry.tryAcquire(sessionId)) {
            throw new ChatException(ChatErrorCode.CHAT_TURN_IN_PROGRESS);
        }
        try {
            requirePublishedSnapshot(session.getWorkspaceId());
            List<ChatMessage> history = chatMessageRepository.findAllBySessionId(sessionId);
            Instant now = Instant.now(clock);
            requireNoTurnInProgress(
                    history,
                    now
            );
            // 검색이 실패하면 아무것도 저장하지 않는다(로드맵 Q30).
            SearchContext searchContext = search(
                    session.getWorkspaceId(),
                    content,
                    ChatSearchQueryComposer.compose(
                            content,
                            history
                    )
            );
            if (searchContext.isReady()) {
                ChatMessage userMessage = chatMessagePersistenceService.saveMessage(
                        sessionId,
                        ChatMessageRole.USER,
                        content,
                        now
                );
                return ChatSearchResult.ready(
                        userMessage.getId(),
                        SearchContext.groundingRules(),
                        searchContext.contextReferences()
                );
            }
            String fallbackAnswer = searchContext.fallbackAnswer();
            ChatFallbackTurn turn = chatMessagePersistenceService.saveFallbackTurn(
                    sessionId,
                    content,
                    fallbackAnswer,
                    now
            );
            return ChatSearchResult.fallback(
                    searchContext.status(),
                    turn.userMessage()
                            .getId(),
                    turn.assistantMessage()
                            .getId(),
                    fallbackAnswer
            );
        } finally {
            activeChatStreamRegistry.release(sessionId);
        }
    }

    private void requireNoTurnInProgress(
            List<ChatMessage> history,
            Instant now
    ) {
        if (history.isEmpty()) {
            return;
        }
        ChatMessage lastMessage = history.getLast();
        if (lastMessage.getRole() != ChatMessageRole.USER) {
            return;
        }
        Instant expiresAt = lastMessage.getCreatedAt()
                .plus(chatProperties.turnTimeout());
        if (now.isBefore(expiresAt)) {
            throw new ChatException(ChatErrorCode.CHAT_TURN_IN_PROGRESS);
        }
    }

    private void requirePublishedSnapshot(long workspaceId) {
        try {
            documentSearchService.requirePublishedSnapshot(workspaceId);
        } catch (SearchException exception) {
            throw translate(exception);
        }
    }

    private SearchContext search(
            long workspaceId,
            String query,
            String searchQuery
    ) {
        try {
            return documentSearchService.search(
                    workspaceId,
                    query,
                    searchQuery
            );
        } catch (SearchException exception) {
            throw translate(exception);
        }
    }

    /** 문서 준비 게이트만 채팅 코드로 바꾸고, 나머지 검색 오류는 코드를 그대로 데스크톱에 보낸다(로드맵 Q31). */
    private RuntimeException translate(SearchException exception) {
        if (exception.searchErrorCode() == SearchErrorCode.SEARCH_IMPORT_NOT_READY) {
            return new ChatException(
                    ChatErrorCode.CHAT_DOCUMENTS_NOT_READY,
                    exception
            );
        }
        return exception;
    }
}
