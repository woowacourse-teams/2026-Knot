package com.knot.backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.chat.application.dto.command.CreateChatSessionCommand;
import com.knot.backend.chat.application.dto.result.ChatSessionResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatSessionServiceTest {
    @Test
    @DisplayName("워크스페이스 멤버가 기본 제목으로 채팅 세션을 생성한다")
    void createSession_success() {
        // given
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ChatSessionAccessPolicy accessPolicy = new ChatSessionAccessPolicy(
                chatSessionRepository,
                workspaceMemberRepository
        );
        ChatSessionService service = new ChatSessionService(
                chatSessionRepository,
                mock(com.knot.backend.chat.domain.ChatMessageRepository.class),
                accessPolicy
        );
        ChatSession savedSession = mock(ChatSession.class);
        when(savedSession.getId()).thenReturn(10L);
        when(savedSession.getTitle()).thenReturn(ChatSession.DEFAULT_TITLE);
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(savedSession);

        // when
        ChatSessionResult result = service.createSession(
                new CreateChatSessionCommand(
                        1L,
                        2L,
                        null
                )
        );

        // then
        assertThat(result.title()).isEqualTo(ChatSession.DEFAULT_TITLE);
        verify(chatSessionRepository).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 채팅 세션 생성을 거부한다")
    void createSession_failure_notWorkspaceMember() {
        // given
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ChatSessionAccessPolicy accessPolicy = new ChatSessionAccessPolicy(
                chatSessionRepository,
                workspaceMemberRepository
        );
        ChatSessionService service = new ChatSessionService(
                chatSessionRepository,
                mock(com.knot.backend.chat.domain.ChatMessageRepository.class),
                accessPolicy
        );
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(false);

        // when
        ThrowingCallable action = () -> service.createSession(
                new CreateChatSessionCommand(
                        1L,
                        2L,
                        null
                )
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.CHAT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("세션 목록이 비어 있으면 빈 목록을 반환한다")
    void findSessions_success_empty() {
        // given
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ChatSessionAccessPolicy accessPolicy = new ChatSessionAccessPolicy(
                chatSessionRepository,
                workspaceMemberRepository
        );
        ChatSessionService service = new ChatSessionService(
                chatSessionRepository,
                mock(com.knot.backend.chat.domain.ChatMessageRepository.class),
                accessPolicy
        );
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                chatSessionRepository.findAllByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(List.of());

        // when
        List<ChatSessionResult> result = service.findSessions(
                1L,
                2L
        );

        // then
        assertThat(result).isEmpty();
    }
}
