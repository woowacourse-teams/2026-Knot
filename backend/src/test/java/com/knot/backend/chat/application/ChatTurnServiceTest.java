package com.knot.backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.chat.application.dto.command.SaveChatTurnCommand;
import com.knot.backend.chat.application.dto.result.ChatTurn;
import com.knot.backend.chat.application.dto.result.ChatTurnResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatMessageRole;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.search.domain.SearchReferenceCandidate;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatTurnServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private static final long SESSION_ID = 10L;
    private static final long WORKSPACE_ID = 1L;
    private static final long OWNER_ID = 2L;
    private static final long OTHER_MEMBER_ID = 3L;
    private static final SearchReferenceCandidate REFERENCE = new SearchReferenceCandidate(
            301L,
            201L,
            3,
            0.95
    );

    private final ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final ChatMessagePersistenceService persistenceService = mock(ChatMessagePersistenceService.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final ActiveChatStreamRegistry registry = new ActiveChatStreamRegistry();
    private final ChatTurnService service = new ChatTurnService(
            new ChatSessionAccessPolicy(
                    chatSessionRepository,
                    workspaceMemberRepository
            ),
            persistenceService,
            chatMessageRepository,
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
    @DisplayName("질문·답변·근거를 한 번에 저장하고 두 메시지 ID를 돌려준 뒤 세션 잠금을 푼다")
    void save_success() {
        // given
        SaveChatTurnCommand command = command(List.of(REFERENCE));
        when(
                persistenceService.saveClientTurn(
                        SESSION_ID,
                        "질문",
                        "답변",
                        NOW,
                        List.of(REFERENCE)
                )
        ).thenReturn(
                turn(
                        101L,
                        102L
                )
        );

        // when
        ChatTurnResult result = service.save(
                SESSION_ID,
                OWNER_ID,
                command
        );

        // then
        assertThat(result.userMessageId()).isEqualTo(101L);
        assertThat(result.messageId()).isEqualTo(102L);
        assertThat(registry.tryAcquire(SESSION_ID)).isTrue();
    }

    @Test
    @DisplayName("근거가 없어도 저장하고, 마지막 메시지가 ASSISTANT면 진행 중 턴으로 보지 않는다")
    void save_success_withoutReferencesAfterCompletedTurn() {
        // given
        when(chatMessageRepository.findAllBySessionId(SESSION_ID)).thenReturn(
                List.of(
                        message(
                                ChatMessageRole.USER,
                                NOW.minus(Duration.ofMinutes(1))
                        ),
                        message(
                                ChatMessageRole.ASSISTANT,
                                NOW.minus(Duration.ofSeconds(30))
                        )
                )
        );
        when(
                persistenceService.saveClientTurn(
                        eq(SESSION_ID),
                        anyString(),
                        anyString(),
                        eq(NOW),
                        eq(List.of())
                )
        ).thenReturn(
                turn(
                        103L,
                        104L
                )
        );

        // when
        ChatTurnResult result = service.save(
                SESSION_ID,
                OWNER_ID,
                command(List.of())
        );

        // then
        assertThat(result.messageId()).isEqualTo(104L);
    }

    @Test
    @DisplayName("timeout이 지난 미완 USER 턴은 진행 중으로 보지 않고 새 턴을 저장한다")
    void save_success_staleTurn() {
        // given
        when(chatMessageRepository.findAllBySessionId(SESSION_ID)).thenReturn(
                List.of(
                        message(
                                ChatMessageRole.USER,
                                NOW.minus(Duration.ofMinutes(6))
                        )
                )
        );
        when(
                persistenceService.saveClientTurn(
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(),
                        anyList()
                )
        ).thenReturn(
                turn(
                        105L,
                        106L
                )
        );

        // when
        ChatTurnResult result = service.save(
                SESSION_ID,
                OWNER_ID,
                command(List.of(REFERENCE))
        );

        // then
        assertThat(result.userMessageId()).isEqualTo(105L);
    }

    @Test
    @DisplayName("답변 없는 USER 메시지가 timeout 안이면 저장 없이 진행 중 턴 오류를 반환하고 잠금을 푼다")
    void save_failure_turnInProgress() {
        // given
        when(chatMessageRepository.findAllBySessionId(SESSION_ID)).thenReturn(
                List.of(
                        message(
                                ChatMessageRole.USER,
                                NOW.minus(Duration.ofMinutes(4))
                        )
                )
        );

        // when
        ThrowingCallable action = () -> service.save(
                SESSION_ID,
                OWNER_ID,
                command(List.of(REFERENCE))
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).chatErrorCode())
                .isEqualTo(ChatErrorCode.CHAT_TURN_IN_PROGRESS);
        verifyNothingSaved();
        assertThat(registry.tryAcquire(SESSION_ID)).isTrue();
    }

    @Test
    @DisplayName("같은 세션의 다른 요청이 잠금을 쥐고 있으면 저장 없이 진행 중 턴 오류를 반환한다")
    void save_failure_sessionLocked() {
        // given
        registry.tryAcquire(SESSION_ID);

        // when
        ThrowingCallable action = () -> service.save(
                SESSION_ID,
                OWNER_ID,
                command(List.of(REFERENCE))
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).chatErrorCode())
                .isEqualTo(ChatErrorCode.CHAT_TURN_IN_PROGRESS);
        verifyNothingSaved();
    }

    @Test
    @DisplayName("세션 소유자가 아니면 접근 거부 오류를 반환한다")
    void save_failure_accessDenied() {
        // when
        ThrowingCallable action = () -> service.save(
                SESSION_ID,
                OTHER_MEMBER_ID,
                command(List.of(REFERENCE))
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).chatErrorCode())
                .isEqualTo(ChatErrorCode.CHAT_ACCESS_DENIED);
        verifyNothingSaved();
    }

    @Test
    @DisplayName("같은 페이지의 같은 청크가 두 번 오면 저장 없이 근거 오류를 반환한다")
    void save_failure_duplicateReference() {
        // when
        ThrowingCallable action = () -> service.save(
                SESSION_ID,
                OWNER_ID,
                command(
                        List.of(
                                REFERENCE,
                                new SearchReferenceCandidate(
                                        301L,
                                        201L,
                                        3,
                                        0.5
                                )
                        )
                )
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).chatErrorCode())
                .isEqualTo(ChatErrorCode.CHAT_TURN_REFERENCE_INVALID);
        verifyNothingSaved();
    }

    @Test
    @DisplayName("같은 페이지라도 청크가 다르면 근거로 받고 9개째부터 거절한다")
    void save_failure_tooManyReferences() {
        // given
        List<SearchReferenceCandidate> nine = IntStream.range(
                0,
                9
        )
                .mapToObj(
                        index -> new SearchReferenceCandidate(
                                301L,
                                201L,
                                index,
                                0.9
                        )
                )
                .toList();

        // when
        ThrowingCallable action = () -> service.save(
                SESSION_ID,
                OWNER_ID,
                command(nine)
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).chatErrorCode())
                .isEqualTo(ChatErrorCode.CHAT_TURN_REFERENCE_INVALID);
        verifyNothingSaved();
    }

    @Test
    @DisplayName("Workspace JOIN에 걸리지 않은 근거는 입력 오류로 번역하고 잠금을 푼다")
    void save_failure_referenceOutsideWorkspace() {
        // given
        when(
                persistenceService.saveClientTurn(
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(),
                        anyList()
                )
        ).thenThrow(new SearchException(SearchErrorCode.SEARCH_REFERENCE_FAILED));

        // when
        ThrowingCallable action = () -> service.save(
                SESSION_ID,
                OWNER_ID,
                command(List.of(REFERENCE))
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).chatErrorCode())
                .isEqualTo(ChatErrorCode.CHAT_TURN_REFERENCE_INVALID);
        assertThat(registry.tryAcquire(SESSION_ID)).isTrue();
    }

    @Test
    @DisplayName("근거 검증 외의 검색 오류는 코드를 바꾸지 않고 그대로 전달한다")
    void save_failure_otherSearchErrorPassesThrough() {
        // given
        when(
                persistenceService.saveClientTurn(
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(),
                        anyList()
                )
        ).thenThrow(new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED));

        // when
        ThrowingCallable action = () -> service.save(
                SESSION_ID,
                OWNER_ID,
                command(List.of(REFERENCE))
        );

        // then
        assertThatThrownBy(action).isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).searchErrorCode())
                .isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED);
        assertThat(registry.tryAcquire(SESSION_ID)).isTrue();
    }

    private void verifyNothingSaved() {
        verify(
                persistenceService,
                never()
        ).saveClientTurn(
                anyLong(),
                anyString(),
                anyString(),
                any(),
                anyList()
        );
    }

    private static SaveChatTurnCommand command(List<SearchReferenceCandidate> references) {
        return new SaveChatTurnCommand(
                "질문",
                "답변",
                references
        );
    }

    private static ChatTurn turn(
            long userMessageId,
            long assistantMessageId
    ) {
        ChatMessage userMessage = mock(ChatMessage.class);
        ChatMessage assistantMessage = mock(ChatMessage.class);
        when(userMessage.getId()).thenReturn(userMessageId);
        when(assistantMessage.getId()).thenReturn(assistantMessageId);
        return new ChatTurn(
                userMessage,
                assistantMessage
        );
    }

    private static ChatMessage message(
            ChatMessageRole role,
            Instant createdAt
    ) {
        ChatMessage message = mock(ChatMessage.class);
        when(message.getRole()).thenReturn(role);
        when(message.getCreatedAt()).thenReturn(createdAt);
        return message;
    }
}
