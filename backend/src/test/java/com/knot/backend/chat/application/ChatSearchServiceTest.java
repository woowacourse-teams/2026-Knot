package com.knot.backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.chat.application.dto.result.ChatFallbackTurn;
import com.knot.backend.chat.application.dto.result.ChatSearchResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatMessageRole;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import com.knot.backend.search.application.PublishedDocumentSearchService;
import com.knot.backend.search.application.SearchContext;
import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.search.domain.SearchResultStatus;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatSearchServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final long SESSION_ID = 10L;
    private static final long WORKSPACE_ID = 1L;
    private static final long OWNER_ID = 2L;
    private static final SearchChunk CHUNK = SearchChunk.retrieved(
            WORKSPACE_ID,
            201L,
            301L,
            3,
            "DB 기술 선정 회의록",
            "https://notion.test/db",
            Instant.parse("2026-09-01T00:00:00Z"),
            "PostgreSQL은 pgvector 확장을 위해 선택했다.",
            0.95
    );

    private final ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final ChatMessagePersistenceService persistenceService = mock(ChatMessagePersistenceService.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final PublishedDocumentSearchService documentSearchService = mock(PublishedDocumentSearchService.class);
    private final ActiveChatStreamRegistry registry = new ActiveChatStreamRegistry();
    private final ChatSearchService service = new ChatSearchService(
            new ChatSessionAccessPolicy(
                    chatSessionRepository,
                    workspaceMemberRepository
            ),
            persistenceService,
            chatMessageRepository,
            documentSearchService,
            registry,
            new ChatProperties(Duration.ofMinutes(5)),
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            )
    );

    @BeforeEach
    void setUpSession() {
        ChatSession session = mock(ChatSession.class);
        when(session.getMemberId()).thenReturn(OWNER_ID);
        when(session.getWorkspaceId()).thenReturn(WORKSPACE_ID);
        when(chatSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        OWNER_ID
                )
        ).thenReturn(true);
        when(chatMessageRepository.findAllBySessionId(SESSION_ID)).thenReturn(List.of());
    }

    @Test
    @DisplayName("근거를 찾으면 USER 메시지를 저장하고 규칙 문장과 청크를 돌려주며 LLM 답변은 저장하지 않는다")
    void search_success_ready() {
        // given
        when(
                documentSearchService.search(
                        WORKSPACE_ID,
                        "질문",
                        "질문"
                )
        ).thenReturn(
                SearchContext.ready(
                        List.of(CHUNK),
                        12000
                )
        );
        ChatMessage userMessage = mock(ChatMessage.class);
        when(userMessage.getId()).thenReturn(101L);
        when(
                persistenceService.saveMessage(
                        SESSION_ID,
                        ChatMessageRole.USER,
                        "질문",
                        NOW
                )
        ).thenReturn(userMessage);

        // when
        ChatSearchResult result = service.search(
                SESSION_ID,
                OWNER_ID,
                "질문"
        );

        // then
        assertThat(result.isReady()).isTrue();
        assertThat(result.userMessageId()).isEqualTo(101L);
        assertThat(result.groundingRules()).isEqualTo(SearchContext.groundingRules());
        assertThat(result.chunks()).extracting(SearchChunk::chunkIndex)
                .containsExactly(3);
        assertThat(result.assistantMessageId()).isNull();
        verify(documentSearchService).requirePublishedSnapshot(WORKSPACE_ID);
        verify(
                persistenceService,
                never()
        ).saveFallbackTurn(
                anyLong(),
                anyString(),
                anyString(),
                any()
        );
        verify(
                persistenceService,
                never()
        ).saveAssistantWithReferences(
                anyLong(),
                anyString(),
                any(),
                any()
        );
        assertThat(registry.tryAcquire(SESSION_ID)).isTrue();
    }

    @Test
    @DisplayName("직전 이력으로 검색 질의를 조립하고 마지막이 ASSISTANT면 진행 중 턴으로 보지 않는다")
    void search_success_composesQueryFromHistory() {
        // given
        when(chatMessageRepository.findAllBySessionId(SESSION_ID)).thenReturn(
                List.of(
                        message(
                                ChatMessageRole.USER,
                                "DB 뭐 써?",
                                NOW.minus(Duration.ofMinutes(1))
                        ),
                        message(
                                ChatMessageRole.ASSISTANT,
                                "PostgreSQL을 씁니다",
                                NOW.minus(Duration.ofSeconds(30))
                        )
                )
        );
        when(
                documentSearchService.search(
                        eq(WORKSPACE_ID),
                        eq("왜?"),
                        anyString()
                )
        ).thenReturn(
                SearchContext.ready(
                        List.of(CHUNK),
                        12000
                )
        );
        ChatMessage userMessage = mock(ChatMessage.class);
        when(userMessage.getId()).thenReturn(103L);
        when(
                persistenceService.saveMessage(
                        anyLong(),
                        any(),
                        anyString(),
                        any()
                )
        ).thenReturn(userMessage);

        // when
        ChatSearchResult result = service.search(
                SESSION_ID,
                OWNER_ID,
                "왜?"
        );

        // then
        assertThat(result.userMessageId()).isEqualTo(103L);
        verify(documentSearchService).search(
                WORKSPACE_ID,
                "왜?",
                "USER: DB 뭐 써?\nASSISTANT: PostgreSQL을 씁니다\n현재 질문: 왜?"
        );
    }

    @Test
    @DisplayName("근거가 없으면 USER와 안내 ASSISTANT를 함께 저장하고 안내 문구를 돌려준다")
    void search_success_noResultSavesFallback() {
        // given
        when(
                documentSearchService.search(
                        WORKSPACE_ID,
                        "무관한 질문",
                        "무관한 질문"
                )
        ).thenReturn(SearchContext.noResult());
        ChatMessage userMessage = mock(ChatMessage.class);
        ChatMessage assistantMessage = mock(ChatMessage.class);
        when(userMessage.getId()).thenReturn(101L);
        when(assistantMessage.getId()).thenReturn(102L);
        when(
                persistenceService.saveFallbackTurn(
                        eq(SESSION_ID),
                        eq("무관한 질문"),
                        anyString(),
                        eq(NOW)
                )
        ).thenReturn(
                new ChatFallbackTurn(
                        userMessage,
                        assistantMessage
                )
        );

        // when
        ChatSearchResult result = service.search(
                SESSION_ID,
                OWNER_ID,
                "무관한 질문"
        );

        // then
        assertThat(result.status()).isEqualTo(SearchResultStatus.NO_RESULT);
        assertThat(result.userMessageId()).isEqualTo(101L);
        assertThat(result.assistantMessageId()).isEqualTo(102L);
        assertThat(result.fallbackAnswer()).contains("찾지 못했습니다");
        assertThat(result.groundingRules()).isNull();
        assertThat(result.chunks()).isEmpty();
        verify(
                persistenceService,
                never()
        ).saveMessage(
                anyLong(),
                any(),
                anyString(),
                any()
        );
    }

    @Test
    @DisplayName("범위가 넓은 질문은 구체화 안내를 저장하고 NEEDS_CLARIFICATION을 돌려준다")
    void search_success_broadQuestionSavesClarification() {
        // given
        when(
                documentSearchService.search(
                        WORKSPACE_ID,
                        "프로젝트 어때?",
                        "프로젝트 어때?"
                )
        ).thenReturn(SearchContext.needsClarification());
        ChatMessage userMessage = mock(ChatMessage.class);
        ChatMessage assistantMessage = mock(ChatMessage.class);
        when(userMessage.getId()).thenReturn(101L);
        when(assistantMessage.getId()).thenReturn(102L);
        when(
                persistenceService.saveFallbackTurn(
                        anyLong(),
                        anyString(),
                        anyString(),
                        any()
                )
        ).thenReturn(
                new ChatFallbackTurn(
                        userMessage,
                        assistantMessage
                )
        );

        // when
        ChatSearchResult result = service.search(
                SESSION_ID,
                OWNER_ID,
                "프로젝트 어때?"
        );

        // then
        assertThat(result.status()).isEqualTo(SearchResultStatus.NEEDS_CLARIFICATION);
        assertThat(result.fallbackAnswer()).contains("범위가 넓어요");
        verify(persistenceService).saveFallbackTurn(
                eq(SESSION_ID),
                eq("프로젝트 어때?"),
                eq(
                        SearchContext.needsClarification()
                                .fallbackAnswer()
                ),
                eq(NOW)
        );
    }

    @Test
    @DisplayName("답변 없는 USER 메시지가 timeout 안이면 검색과 저장 없이 진행 중 턴 오류를 반환한다")
    void search_failure_turnInProgress() {
        // given
        when(chatMessageRepository.findAllBySessionId(SESSION_ID)).thenReturn(
                List.of(
                        message(
                                ChatMessageRole.USER,
                                "아직 답이 없는 질문",
                                NOW.minus(Duration.ofMinutes(4))
                        )
                )
        );

        // when
        ThrowingCallable action = () -> service.search(
                SESSION_ID,
                OWNER_ID,
                "새 질문"
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.CHAT_TURN_IN_PROGRESS)
        );
        verify(
                documentSearchService,
                never()
        ).search(
                anyLong(),
                anyString(),
                anyString()
        );
        verifyNoInteractions(persistenceService);
        assertThat(registry.tryAcquire(SESSION_ID)).isTrue();
    }

    @Test
    @DisplayName("timeout이 지난 미완 턴은 진행 중으로 보지 않고 새 질문을 받는다")
    void search_success_staleTurnIsAccepted() {
        // given
        when(chatMessageRepository.findAllBySessionId(SESSION_ID)).thenReturn(
                List.of(
                        message(
                                ChatMessageRole.USER,
                                "오래된 미완 질문",
                                NOW.minus(Duration.ofMinutes(5))
                        )
                )
        );
        when(
                documentSearchService.search(
                        eq(WORKSPACE_ID),
                        eq("새 질문"),
                        anyString()
                )
        ).thenReturn(
                SearchContext.ready(
                        List.of(CHUNK),
                        12000
                )
        );
        ChatMessage userMessage = mock(ChatMessage.class);
        when(userMessage.getId()).thenReturn(105L);
        when(
                persistenceService.saveMessage(
                        anyLong(),
                        any(),
                        anyString(),
                        any()
                )
        ).thenReturn(userMessage);

        // when
        ChatSearchResult result = service.search(
                SESSION_ID,
                OWNER_ID,
                "새 질문"
        );

        // then
        assertThat(result.isReady()).isTrue();
        assertThat(result.userMessageId()).isEqualTo(105L);
    }

    @Test
    @DisplayName("같은 세션의 SSE 스트림이 진행 중이면 진행 중 턴 오류를 반환한다")
    void search_failure_sessionLockedByStream() {
        // given
        registry.tryAcquire(SESSION_ID);

        // when
        ThrowingCallable action = () -> service.search(
                SESSION_ID,
                OWNER_ID,
                "질문"
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.CHAT_TURN_IN_PROGRESS)
        );
        verifyNoInteractions(
                documentSearchService,
                persistenceService
        );
    }

    @Test
    @DisplayName("공개된 문서가 없으면 문서 준비 오류를 반환하고 아무것도 저장하지 않는다")
    void search_failure_documentsNotReady() {
        // given
        doThrow(new SearchException(SearchErrorCode.SEARCH_IMPORT_NOT_READY)).when(documentSearchService)
                .requirePublishedSnapshot(WORKSPACE_ID);

        // when
        ThrowingCallable action = () -> service.search(
                SESSION_ID,
                OWNER_ID,
                "질문"
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.CHAT_DOCUMENTS_NOT_READY)
        );
        verifyNoInteractions(persistenceService);
        assertThat(registry.tryAcquire(SESSION_ID)).isTrue();
    }

    @Test
    @DisplayName("검색 자체가 실패하면 검색 오류 코드를 그대로 던지고 USER 메시지를 저장하지 않는다")
    void search_failure_providerFailedSavesNothing() {
        // given
        when(
                documentSearchService.search(
                        WORKSPACE_ID,
                        "질문",
                        "질문"
                )
        ).thenThrow(new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED));

        // when
        ThrowingCallable action = () -> service.search(
                SESSION_ID,
                OWNER_ID,
                "질문"
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );
        verifyNoInteractions(persistenceService);
        assertThat(registry.tryAcquire(SESSION_ID)).isTrue();
    }

    @Test
    @DisplayName("세션 소유자가 아니면 접근 거부 오류를 반환한다")
    void search_failure_accessDenied() {
        // given
        long otherMemberId = 3L;

        // when
        ThrowingCallable action = () -> service.search(
                SESSION_ID,
                otherMemberId,
                "질문"
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.CHAT_ACCESS_DENIED)
        );
        verifyNoInteractions(
                documentSearchService,
                persistenceService
        );
    }

    private static ChatMessage message(
            ChatMessageRole role,
            String content,
            Instant createdAt
    ) {
        return ChatMessage.create(
                SESSION_ID,
                role,
                content,
                createdAt
        );
    }
}
