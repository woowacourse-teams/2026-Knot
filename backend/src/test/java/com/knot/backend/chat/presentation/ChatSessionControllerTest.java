package com.knot.backend.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.ChatSessionService;
import com.knot.backend.chat.application.dto.command.CreateChatSessionCommand;
import com.knot.backend.chat.application.dto.result.ChatSessionResult;
import com.knot.backend.chat.presentation.dto.request.CreateChatSessionRequest;
import com.knot.backend.chat.presentation.dto.response.ChatSessionResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ChatSessionControllerTest {
    @Test
    @DisplayName("채팅 세션 생성 성공 시 201과 조회 Location을 반환한다")
    void createSession_success() {
        // given
        ChatSessionService service = mock(ChatSessionService.class);
        ChatSessionController controller = new ChatSessionController(service);
        ChatSessionResult session = new ChatSessionResult(
                10L,
                "새 대화",
                Instant.parse("2026-08-30T00:00:00Z"),
                Instant.parse("2026-08-30T00:00:00Z")
        );
        when(service.createSession(any(CreateChatSessionCommand.class))).thenReturn(session);

        // when
        ResponseEntity<ChatSessionResponse> result = controller.createSession(
                1L,
                new CreateChatSessionRequest(null),
                AuthenticatedMember.of(
                        2L,
                        "흑곰",
                        null
                )
        );

        // then
        assertThat(
                result.getStatusCode()
                        .value()
        ).isEqualTo(201);
        assertThat(
                result.getHeaders()
                        .getLocation()
        ).hasToString("/api/conversations/10");
        assertThat(result.getBody()).extracting(ChatSessionResponse::title)
                .isEqualTo("새 대화");
    }

    @Test
    @DisplayName("채팅 세션 목록이 없으면 빈 배열을 반환한다")
    void findSessions_success_empty() {
        // given
        ChatSessionService service = mock(ChatSessionService.class);
        ChatSessionController controller = new ChatSessionController(service);
        when(
                service.findSessions(
                        1L,
                        2L
                )
        ).thenReturn(List.of());

        // when
        List<ChatSessionResponse> result = controller.findSessions(
                1L,
                AuthenticatedMember.of(
                        2L,
                        "흑곰",
                        null
                )
        );

        // then
        assertThat(result).isEmpty();
    }
}
