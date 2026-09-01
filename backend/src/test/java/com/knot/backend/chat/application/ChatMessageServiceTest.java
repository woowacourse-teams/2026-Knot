package com.knot.backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatMessageServiceTest {

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
