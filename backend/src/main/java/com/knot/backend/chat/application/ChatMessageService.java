package com.knot.backend.chat.application;

import com.knot.backend.chat.application.dto.command.LlmMessage;
import com.knot.backend.chat.application.dto.command.LlmMessageRole;
import com.knot.backend.chat.application.dto.command.LlmRequest;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatMessageRole;
import com.knot.backend.search.application.PublishedDocumentSearchService;
import com.knot.backend.search.application.SearchContext;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.chat.domain.ChatSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ChatMessageService {
    private static final int MAX_SEARCH_HISTORY_MESSAGES = 4;
    private static final int MAX_SEARCH_QUERY_CHARACTERS = 4000;

    private final ChatSessionAccessPolicy chatSessionAccessPolicy;
    private final ChatMessagePersistenceService chatMessagePersistenceService;
    private final ChatMessageRepository chatMessageRepository;
    private final LlmClient llmClient;
    private final PublishedDocumentSearchService documentSearchService;
    private final ActiveChatStreamRegistry activeChatStreamRegistry;
    private final Executor chatStreamExecutor;

    public ChatMessageService(
            ChatSessionAccessPolicy chatSessionAccessPolicy,
            ChatMessagePersistenceService chatMessagePersistenceService,
            ChatMessageRepository chatMessageRepository,
            LlmClient llmClient,
            PublishedDocumentSearchService documentSearchService,
            ActiveChatStreamRegistry activeChatStreamRegistry,
            @Qualifier("chatStreamExecutor") Executor chatStreamExecutor
    ) {
        this.chatSessionAccessPolicy = chatSessionAccessPolicy;
        this.chatMessagePersistenceService = chatMessagePersistenceService;
        this.chatMessageRepository = chatMessageRepository;
        this.llmClient = llmClient;
        this.documentSearchService = documentSearchService;
        this.activeChatStreamRegistry = activeChatStreamRegistry;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    public ChatStreamHandle sendMessage(
            long sessionId,
            long memberId,
            String content,
            ChatStreamListener listener
    ) {
        ChatSession session = chatSessionAccessPolicy.requireOwner(
                sessionId,
                memberId
        );
        if (!activeChatStreamRegistry.tryAcquire(sessionId)) {
            throw new ChatException(ChatErrorCode.CHAT_STREAM_ALREADY_ACTIVE);
        }

        AtomicBoolean streamStarted = new AtomicBoolean();
        AtomicBoolean streamReleased = new AtomicBoolean();
        Runnable releaseStream = () -> {
            if (streamReleased.compareAndSet(
                    false,
                    true
            )) {
                activeChatStreamRegistry.release(sessionId);
            }
        };
        try {
            requirePublishedSnapshot(session.getWorkspaceId());
            chatMessagePersistenceService.saveMessage(
                    sessionId,
                    ChatMessageRole.USER,
                    content,
                    Instant.now()
            );
            List<ChatMessage> history = chatMessageRepository.findAllBySessionId(sessionId);
            AtomicReference<Future<?>> futureReference = new AtomicReference<>();
            AtomicReference<LlmStream> streamReference = new AtomicReference<>();
            ChatStreamHandle handle = new ChatStreamHandle(() -> {
                streamStarted.compareAndSet(
                        false,
                        true
                );
                try {
                    cancel(futureReference);
                } finally {
                    try {
                        closeStream(streamReference);
                    } finally {
                        releaseStream.run();
                    }
                }
            });
            FutureTask<Void> task = new FutureTask<>(() -> {
                if (!streamStarted.compareAndSet(
                        false,
                        true
                )) {
                    return null;
                }
                try {
                    streamAnswer(
                            sessionId,
                            session.getWorkspaceId(),
                            content,
                            history,
                            listener,
                            handle,
                            streamReference
                    );
                } finally {
                    releaseStream.run();
                }
                return null;
            });
            futureReference.set(task);
            chatStreamExecutor.execute(task);
            return handle;
        } catch (RejectedExecutionException exception) {
            releaseStream.run();
            throw new ChatException(
                    ChatErrorCode.LLM_STREAM_FAILED,
                    exception
            );
        } catch (RuntimeException exception) {
            releaseStream.run();
            throw exception;
        }
    }

    private void requirePublishedSnapshot(long workspaceId) {
        try {
            documentSearchService.requirePublishedSnapshot(workspaceId);
        } catch (SearchException exception) {
            throw new ChatException(
                    mapSearchError(exception),
                    exception
            );
        }
    }

    private void streamAnswer(
            long sessionId,
            long workspaceId,
            String query,
            List<ChatMessage> history,
            ChatStreamListener listener,
            ChatStreamHandle handle,
            AtomicReference<LlmStream> streamReference
    ) {
        StringBuilder answer = new StringBuilder();
        try {
            SearchContext searchContext = documentSearchService.search(
                    workspaceId,
                    query,
                    toSearchQuery(
                            query,
                            history
                    )
            );
            if (!searchContext.isReady()) {
                completeFallback(
                        sessionId,
                        searchContext,
                        listener,
                        handle
                );
                return;
            }
            LlmStream stream = llmClient.start(
                    toLlmRequest(
                            history,
                            searchContext
                    )
            );
            streamReference.set(stream);
            if (handle.isCancelled()) {
                return;
            }
            while (stream.hasNext()) {
                checkCancellation(handle);
                String delta = stream.next();
                if (delta == null || delta.isEmpty()) {
                    continue;
                }
                answer.append(delta);
                if (!listener.onChunk(delta)) {
                    handle.cancel();
                    return;
                }
            }

            if (!handle.beginCompletion()) {
                return;
            }
            ChatMessage assistantMessage = chatMessagePersistenceService.saveAssistantWithReferences(
                    sessionId,
                    answer.toString(),
                    Instant.now(),
                    searchContext.references()
            );
            listener.onComplete(assistantMessage.getId());
        } catch (CancellationException exception) {
            if (Thread.currentThread()
                    .isInterrupted()) {
                Thread.currentThread()
                        .interrupt();
            }
        } catch (SearchException exception) {
            if (!handle.isCancelled()) {
                listener.onError(mapSearchError(exception));
            }
        } catch (RuntimeException exception) {
            if (!handle.isCancelled()) {
                listener.onError(ChatErrorCode.LLM_STREAM_FAILED);
            }
        } finally {
            closeStream(streamReference);
        }
    }

    private void completeFallback(
            long sessionId,
            SearchContext searchContext,
            ChatStreamListener listener,
            ChatStreamHandle handle
    ) {
        if (!handle.beginCompletion()) {
            return;
        }
        String fallbackAnswer = searchContext.fallbackAnswer();
        if (!listener.onChunk(fallbackAnswer)) {
            handle.cancel();
            return;
        }
        ChatMessage assistantMessage = chatMessagePersistenceService.saveAssistantWithReferences(
                sessionId,
                fallbackAnswer,
                Instant.now(),
                List.of()
        );
        listener.onComplete(assistantMessage.getId());
    }

    private ChatErrorCode mapSearchError(SearchException exception) {
        if (exception.searchErrorCode() == SearchErrorCode.SEARCH_IMPORT_NOT_READY) {
            return ChatErrorCode.CHAT_DOCUMENTS_NOT_READY;
        }
        return ChatErrorCode.LLM_STREAM_FAILED;
    }

    private LlmRequest toLlmRequest(
            List<ChatMessage> history,
            SearchContext searchContext
    ) {
        List<LlmMessage> messages = new java.util.ArrayList<>();
        messages.add(
                new LlmMessage(
                        LlmMessageRole.SYSTEM,
                        searchContext.groundingPrompt()
                )
        );
        messages.addAll(
                history.stream()
                        .map(
                                message -> new LlmMessage(
                                        LlmMessageRole.valueOf(
                                                message.getRole()
                                                        .name()
                                        ),
                                        message.getContent()
                                )
                        )
                        .toList()
        );
        return new LlmRequest(messages);
    }

    private String toSearchQuery(
            String query,
            List<ChatMessage> history
    ) {
        int currentMessageIndex = history.size() - 1;
        if (currentMessageIndex <= 0) {
            return query;
        }
        int firstMessageIndex = Math.max(
                0,
                currentMessageIndex - MAX_SEARCH_HISTORY_MESSAGES
        );
        List<String> previousMessages = new ArrayList<>();
        for (int index = firstMessageIndex; index < currentMessageIndex; index++) {
            ChatMessage message = history.get(index);
            previousMessages.add(message.getRole() + ": " + message.getContent());
        }
        String currentQuestion = "현재 질문: " + query;
        String previousContext = String.join(
                "\n",
                previousMessages
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

    private void checkCancellation(ChatStreamHandle handle) {
        if (handle.isCancelled() || Thread.currentThread()
                .isInterrupted()) {
            throw new CancellationException();
        }
    }

    private void cancel(AtomicReference<Future<?>> futureReference) {
        Future<?> future = futureReference.get();
        if (future != null) {
            future.cancel(true);
        }
    }

    private void closeStream(AtomicReference<LlmStream> streamReference) {
        LlmStream stream = streamReference.getAndSet(null);
        if (stream != null) {
            stream.close();
        }
    }
}
