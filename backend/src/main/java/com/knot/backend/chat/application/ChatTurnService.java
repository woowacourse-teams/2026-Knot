package com.knot.backend.chat.application;

import com.knot.backend.chat.application.dto.command.SaveChatTurnCommand;
import com.knot.backend.chat.application.dto.result.ChatTurn;
import com.knot.backend.chat.application.dto.result.ChatTurnResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.search.domain.SearchReferenceCandidate;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * CLI 에이전트 경유 탐색의 턴 저장 단계(기획서 6.4, 로드맵 S2). 세션 소유자·진행 중 턴을 검사하고 질문·답변(CLIENT)·근거를
 * 한 트랜잭션에 저장한다. 서버가 돌려준 8개의 부분집합인지, 에이전트가 실제로 그 근거를 읽었는지는 검증하지 않는다(로드맵
 * Q24·R17). LLM·검색은 부르지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChatTurnService {
    static final int MAX_REFERENCES = 8;

    private final ChatSessionAccessPolicy chatSessionAccessPolicy;
    private final ChatMessagePersistenceService chatMessagePersistenceService;
    private final ChatMessageRepository chatMessageRepository;
    private final ActiveChatStreamRegistry activeChatStreamRegistry;
    private final ChatProperties chatProperties;
    private final Clock clock;

    public ChatTurnResult save(
            long sessionId,
            long memberId,
            SaveChatTurnCommand command
    ) {
        chatProperties.validate();
        chatSessionAccessPolicy.requireOwner(
                sessionId,
                memberId
        );
        requireDistinctReferences(command.references());
        // 검색 API·SSE 경로와 같은 잠금으로 같은 세션의 동시 진입을 막는다(로드맵 Q23).
        if (!activeChatStreamRegistry.tryAcquire(sessionId)) {
            throw new ChatException(ChatErrorCode.CHAT_TURN_IN_PROGRESS);
        }
        try {
            List<ChatMessage> history = chatMessageRepository.findAllBySessionId(sessionId);
            Instant now = Instant.now(clock);
            ChatTurnGuard.requireNoTurnInProgress(
                    history,
                    now,
                    chatProperties.turnTimeout()
            );
            ChatTurn turn = saveTurn(
                    sessionId,
                    command,
                    now
            );
            return ChatTurnResult.from(turn);
        } finally {
            activeChatStreamRegistry.release(sessionId);
        }
    }

    /** 같은 (페이지, 청크)가 두 번 오면 DB 유일 키 위반(500)이 되기 전에 입력 오류(400)로 거절한다(로드맵 Q52). */
    private void requireDistinctReferences(List<SearchReferenceCandidate> references) {
        if (references.size() > MAX_REFERENCES) {
            throw new ChatException(ChatErrorCode.CHAT_TURN_REFERENCE_INVALID);
        }
        Set<String> seen = new HashSet<>();
        for (SearchReferenceCandidate reference : references) {
            if (reference.importRunId() == null || reference.importedPageId() == null || reference.chunkIndex() < 0) {
                throw new ChatException(ChatErrorCode.CHAT_TURN_REFERENCE_INVALID);
            }
            if (!seen.add(reference.importedPageId() + ":" + reference.chunkIndex())) {
                throw new ChatException(ChatErrorCode.CHAT_TURN_REFERENCE_INVALID);
            }
        }
    }

    private ChatTurn saveTurn(
            long sessionId,
            SaveChatTurnCommand command,
            Instant now
    ) {
        try {
            return chatMessagePersistenceService.saveClientTurn(
                    sessionId,
                    command.question(),
                    command.answer(),
                    now,
                    command.references()
            );
        } catch (SearchException exception) {
            // Workspace JOIN에 걸리지 않은 근거는 클라이언트 입력 오류다. 트랜잭션은 이미 되돌아갔다.
            if (exception.searchErrorCode() == SearchErrorCode.SEARCH_REFERENCE_FAILED) {
                throw new ChatException(
                        ChatErrorCode.CHAT_TURN_REFERENCE_INVALID,
                        exception
                );
            }
            throw exception;
        }
    }
}
