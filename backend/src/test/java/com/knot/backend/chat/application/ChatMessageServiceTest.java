package com.knot.backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatMessageRole;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import com.knot.backend.chat.application.dto.command.LlmRequest;
import com.knot.backend.search.application.PublishedDocumentSearchService;
import com.knot.backend.search.application.SearchContext;
import com.knot.backend.search.application.SearchReferencePersistenceService;
import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import java.time.Instant;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatMessageServiceTest {
    private final PublishedDocumentSearchService documentSearchService = mock(PublishedDocumentSearchService.class);
    private final SearchReferencePersistenceService searchReferencePersistenceService = mock(
            SearchReferencePersistenceService.class
    );

    @BeforeEach
    void setUpSearchContext() {
        when(
                documentSearchService.search(
                        anyLong(),
                        anyString()
                )
        ).thenReturn(
                SearchContext.ready(
                        List.of(),
                        10000
                )
        );
    }

    @Test
    @DisplayName("메시지를 저장한 뒤 fake LLM chunk와 완료 이벤트를 전달한다")
    void sendMessage_success() {
        // given
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ChatSessionAccessPolicy accessPolicy = new ChatSessionAccessPolicy(
                chatSessionRepository,
                workspaceMemberRepository
        );
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        ChatMessagePersistenceService persistenceService = mock(ChatMessagePersistenceService.class);
        LlmClient llmClient = mock(LlmClient.class);
        LlmStream llmStream = mock(LlmStream.class);
        ChatStreamListener listener = mock(ChatStreamListener.class);
        ChatMessage userMessage = mock(ChatMessage.class);
        ChatMessage assistantMessage = mock(ChatMessage.class);
        ChatSession session = mock(ChatSession.class);
        when(session.getMemberId()).thenReturn(2L);
        when(session.getWorkspaceId()).thenReturn(1L);
        when(userMessage.getRole()).thenReturn(ChatMessageRole.USER);
        when(userMessage.getContent()).thenReturn("질문");
        when(chatSessionRepository.findById(10L)).thenReturn(java.util.Optional.of(session));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                persistenceService.saveMessage(
                        anyLong(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(
                userMessage,
                assistantMessage
        );
        when(chatMessageRepository.findAllBySessionId(10L)).thenReturn(List.of(userMessage));
        when(llmClient.start(any())).thenReturn(llmStream);
        when(llmStream.hasNext()).thenReturn(
                true,
                true,
                false
        );
        when(llmStream.next()).thenReturn(
                "첫 ",
                "응답"
        );
        when(listener.onChunk(any())).thenReturn(true);
        when(assistantMessage.getId()).thenReturn(100L);
        Executor directExecutor = Runnable::run;
        ChatMessageService service = new ChatMessageService(
                accessPolicy,
                persistenceService,
                chatMessageRepository,
                llmClient,
                documentSearchService,
                searchReferencePersistenceService,
                new ActiveChatStreamRegistry(),
                directExecutor
        );

        // when
        ChatStreamHandle handle = service.sendMessage(
                10L,
                2L,
                "질문",
                listener
        );

        // then
        assertThat(handle.isCancelled()).isFalse();
        verify(listener).onChunk("첫 ");
        verify(listener).onChunk("응답");
        verify(listener).onComplete(100L);
        verify(
                listener,
                never()
        ).onError(any(ChatErrorCode.class));
        verify(persistenceService).saveMessage(
                anyLong(),
                org.mockito.ArgumentMatchers.eq(ChatMessageRole.USER),
                any(),
                any()
        );
        verify(persistenceService).saveMessage(
                anyLong(),
                org.mockito.ArgumentMatchers.eq(ChatMessageRole.ASSISTANT),
                org.mockito.ArgumentMatchers.eq("첫 응답"),
                any()
        );
    }

    @Test
    @DisplayName("검색된 최대 근거를 system prompt에 넣고 답변 완료 시 출처를 저장한다")
    void sendMessage_success_injectsGroundingAndPersistsReferences() {
        // given
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ChatSession session = mock(ChatSession.class);
        when(session.getMemberId()).thenReturn(2L);
        when(session.getWorkspaceId()).thenReturn(1L);
        when(chatSessionRepository.findById(10L)).thenReturn(java.util.Optional.of(session));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        ChatMessagePersistenceService persistenceService = mock(ChatMessagePersistenceService.class);
        ChatMessage userMessage = mock(ChatMessage.class);
        ChatMessage assistantMessage = mock(ChatMessage.class);
        when(userMessage.getRole()).thenReturn(ChatMessageRole.USER);
        when(userMessage.getContent()).thenReturn("PostgreSQL을 왜 사용했지?");
        when(assistantMessage.getId()).thenReturn(101L);
        when(
                persistenceService.saveMessage(
                        anyLong(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(
                userMessage,
                assistantMessage
        );
        when(chatMessageRepository.findAllBySessionId(10L)).thenReturn(List.of(userMessage));
        LlmClient llmClient = mock(LlmClient.class);
        LlmStream llmStream = mock(LlmStream.class);
        when(llmClient.start(any())).thenReturn(llmStream);
        when(llmStream.hasNext()).thenReturn(false);
        ChatStreamListener listener = mock(ChatStreamListener.class);
        SearchChunk reference = SearchChunk.retrieved(
                1L,
                201L,
                301L,
                0,
                "DB 기술 선정 회의록",
                "https://notion.test/db",
                Instant.parse("2026-09-01T00:00:00Z"),
                "PostgreSQL은 관계형 데이터 관리와 pgvector 확장을 위해 선택했다.",
                0.95
        );
        when(
                documentSearchService.search(
                        1L,
                        "질문"
                )
        ).thenReturn(
                SearchContext.ready(
                        List.of(reference),
                        10000
                )
        );
        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        when(listener.onChunk(any())).thenReturn(true);
        ChatMessageService service = new ChatMessageService(
                new ChatSessionAccessPolicy(
                        chatSessionRepository,
                        workspaceMemberRepository
                ),
                persistenceService,
                chatMessageRepository,
                llmClient,
                documentSearchService,
                searchReferencePersistenceService,
                new ActiveChatStreamRegistry(),
                Runnable::run
        );

        // when
        service.sendMessage(
                10L,
                2L,
                "질문",
                listener
        );

        // then
        verify(llmClient).start(requestCaptor.capture());
        assertThat(
                requestCaptor.getValue()
                        .messages()
        ).hasSize(2);
        assertThat(
                requestCaptor.getValue()
                        .messages()
                        .getFirst()
                        .role()
        ).isEqualTo(com.knot.backend.chat.application.dto.command.LlmMessageRole.SYSTEM);
        assertThat(
                requestCaptor.getValue()
                        .messages()
                        .getFirst()
                        .content()
        ).contains("DB 기술 선정 회의록")
                .contains("pgvector");
        verify(searchReferencePersistenceService).replace(
                101L,
                List.of(reference)
        );
    }

    @Test
    @DisplayName("최초 동기화 전에는 LLM을 호출하지 않고 문서 준비 오류를 전달한다")
    void sendMessage_failure_documentsNotReady() {
        // given
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ChatSession session = mock(ChatSession.class);
        when(session.getMemberId()).thenReturn(2L);
        when(session.getWorkspaceId()).thenReturn(1L);
        when(chatSessionRepository.findById(10L)).thenReturn(java.util.Optional.of(session));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        ChatMessagePersistenceService persistenceService = mock(ChatMessagePersistenceService.class);
        when(
                persistenceService.saveMessage(
                        anyLong(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(mock(ChatMessage.class));
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        when(chatMessageRepository.findAllBySessionId(10L)).thenReturn(List.of());
        when(
                documentSearchService.search(
                        1L,
                        "질문"
                )
        ).thenThrow(new SearchException(SearchErrorCode.SEARCH_IMPORT_NOT_READY));
        LlmClient llmClient = mock(LlmClient.class);
        ChatStreamListener listener = mock(ChatStreamListener.class);
        ChatMessageService service = new ChatMessageService(
                new ChatSessionAccessPolicy(
                        chatSessionRepository,
                        workspaceMemberRepository
                ),
                persistenceService,
                chatMessageRepository,
                llmClient,
                documentSearchService,
                searchReferencePersistenceService,
                new ActiveChatStreamRegistry(),
                Runnable::run
        );

        // when
        service.sendMessage(
                10L,
                2L,
                "질문",
                listener
        );

        // then
        verify(listener).onError(ChatErrorCode.CHAT_DOCUMENTS_NOT_READY);
        verify(
                llmClient,
                never()
        ).start(any());
        verify(
                persistenceService,
                never()
        ).saveMessage(
                anyLong(),
                org.mockito.ArgumentMatchers.eq(ChatMessageRole.ASSISTANT),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("LLM 오류가 발생하면 assistant 저장 없이 오류 이벤트를 전달한다")
    void sendMessage_failure_llmError() {
        // given
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ChatSessionAccessPolicy accessPolicy = new ChatSessionAccessPolicy(
                chatSessionRepository,
                workspaceMemberRepository
        );
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        ChatMessagePersistenceService persistenceService = mock(ChatMessagePersistenceService.class);
        LlmClient llmClient = mock(LlmClient.class);
        ChatStreamListener listener = mock(ChatStreamListener.class);
        ChatMessage userMessage = mock(ChatMessage.class);
        ChatSession session = mock(ChatSession.class);
        when(session.getMemberId()).thenReturn(2L);
        when(session.getWorkspaceId()).thenReturn(1L);
        when(chatSessionRepository.findById(10L)).thenReturn(java.util.Optional.of(session));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                persistenceService.saveMessage(
                        anyLong(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(userMessage);
        when(chatMessageRepository.findAllBySessionId(10L)).thenReturn(List.of(userMessage));
        when(llmClient.start(any())).thenThrow(new RuntimeException("provider failure"));
        Executor directExecutor = Runnable::run;
        ChatMessageService service = new ChatMessageService(
                accessPolicy,
                persistenceService,
                chatMessageRepository,
                llmClient,
                documentSearchService,
                searchReferencePersistenceService,
                new ActiveChatStreamRegistry(),
                directExecutor
        );

        // when
        service.sendMessage(
                10L,
                2L,
                "질문",
                listener
        );

        // then
        verify(listener).onError(ChatErrorCode.LLM_STREAM_FAILED);
        verify(persistenceService).saveMessage(
                anyLong(),
                org.mockito.ArgumentMatchers.eq(ChatMessageRole.USER),
                any(),
                any()
        );
        verify(
                persistenceService,
                never()
        ).saveMessage(
                anyLong(),
                org.mockito.ArgumentMatchers.eq(ChatMessageRole.ASSISTANT),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("실행 전 스트림이 취소되어도 활성 스트림 점유가 남지 않는다")
    void sendMessage_cancelBeforeExecution_releasesRegistry() {
        // given
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ChatSession session = mock(ChatSession.class);
        when(session.getMemberId()).thenReturn(2L);
        when(session.getWorkspaceId()).thenReturn(1L);
        when(chatSessionRepository.findById(10L)).thenReturn(java.util.Optional.of(session));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        ChatMessagePersistenceService persistenceService = mock(ChatMessagePersistenceService.class);
        when(
                persistenceService.saveMessage(
                        anyLong(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(mock(ChatMessage.class));
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        when(chatMessageRepository.findAllBySessionId(10L)).thenReturn(List.of());
        ActiveChatStreamRegistry registry = new ActiveChatStreamRegistry();
        ChatMessageService service = new ChatMessageService(
                new ChatSessionAccessPolicy(
                        chatSessionRepository,
                        workspaceMemberRepository
                ),
                persistenceService,
                chatMessageRepository,
                mock(LlmClient.class),
                documentSearchService,
                searchReferencePersistenceService,
                registry,
                command -> {
                }
        );

        // when
        ChatStreamHandle handle = service.sendMessage(
                10L,
                2L,
                "질문",
                mock(ChatStreamListener.class)
        );

        // then
        assertThat(handle.cancel()).isTrue();
        assertThat(registry.tryAcquire(10L)).isTrue();
    }

    @Test
    @DisplayName("블로킹 중인 LLM 스트림을 취소하면 스트림을 닫고 활성 스트림 점유를 해제한다")
    void sendMessage_cancelWhileStreamBlocks_closesStreamAndReleasesRegistry() throws Exception {
        // given
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ChatSession session = mock(ChatSession.class);
        when(session.getMemberId()).thenReturn(2L);
        when(session.getWorkspaceId()).thenReturn(1L);
        when(chatSessionRepository.findById(10L)).thenReturn(java.util.Optional.of(session));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        ChatMessagePersistenceService persistenceService = mock(ChatMessagePersistenceService.class);
        when(
                persistenceService.saveMessage(
                        anyLong(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(mock(ChatMessage.class));
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        when(chatMessageRepository.findAllBySessionId(10L)).thenReturn(List.of());
        LlmClient llmClient = mock(LlmClient.class);
        LlmStream llmStream = mock(LlmStream.class);
        CountDownLatch hasNextEntered = new CountDownLatch(1);
        CountDownLatch allowHasNextExit = new CountDownLatch(1);
        CountDownLatch closeCalled = new CountDownLatch(1);
        when(llmClient.start(any())).thenReturn(llmStream);
        when(llmStream.hasNext()).thenAnswer(invocation -> {
            hasNextEntered.countDown();
            while (allowHasNextExit.getCount() > 0) {
                try {
                    allowHasNextExit.await(
                            100,
                            TimeUnit.MILLISECONDS
                    );
                } catch (InterruptedException exception) {
                    continue;
                }
            }
            return false;
        });
        doAnswer(invocation -> {
            closeCalled.countDown();
            return null;
        }).when(llmStream)
                .close();
        ActiveChatStreamRegistry registry = new ActiveChatStreamRegistry();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ChatMessageService service = new ChatMessageService(
                new ChatSessionAccessPolicy(
                        chatSessionRepository,
                        workspaceMemberRepository
                ),
                persistenceService,
                chatMessageRepository,
                llmClient,
                documentSearchService,
                searchReferencePersistenceService,
                registry,
                executor
        );

        try {
            // when
            ChatStreamHandle handle = service.sendMessage(
                    10L,
                    2L,
                    "질문",
                    mock(ChatStreamListener.class)
            );
            assertThat(
                    hasNextEntered.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
            boolean cancelled = handle.cancel();

            // then
            assertThat(cancelled).isTrue();
            assertThat(
                    closeCalled.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
            assertThat(registry.tryAcquire(10L)).isTrue();
            verify(
                    persistenceService,
                    never()
            ).saveMessage(
                    anyLong(),
                    org.mockito.ArgumentMatchers.eq(ChatMessageRole.ASSISTANT),
                    any(),
                    any()
            );
        } finally {
            allowHasNextExit.countDown();
            executor.shutdown();
            assertThat(
                    executor.awaitTermination(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
            verify(llmStream).close();
        }
    }
}
