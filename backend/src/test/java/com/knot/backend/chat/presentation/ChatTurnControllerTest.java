package com.knot.backend.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.ChatTurnService;
import com.knot.backend.chat.application.dto.command.SaveChatTurnCommand;
import com.knot.backend.chat.application.dto.result.ChatTurnResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.presentation.dto.request.SaveChatTurnReferenceRequest;
import com.knot.backend.chat.presentation.dto.request.SaveChatTurnRequest;
import com.knot.backend.chat.presentation.dto.response.ChatTurnResponse;
import com.knot.backend.search.domain.SearchReferenceCandidate;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ChatTurnControllerTest {
    private static final AuthenticatedMember OWNER = AuthenticatedMember.of(
            2L,
            "흑곰",
            null
    );

    @Test
    @DisplayName("요청의 근거를 순서대로 명령으로 옮겨 저장하고 두 메시지 ID를 201로 반환한다")
    void save_success() {
        // given
        ChatTurnService service = Mockito.mock(ChatTurnService.class);
        ChatTurnController controller = new ChatTurnController(service);
        SaveChatTurnRequest request = new SaveChatTurnRequest(
                "질문",
                "답변",
                List.of(
                        new SaveChatTurnReferenceRequest(
                                301L,
                                201L,
                                2,
                                0.95
                        ),
                        new SaveChatTurnReferenceRequest(
                                301L,
                                202L,
                                0,
                                0.8
                        )
                )
        );
        SaveChatTurnCommand expectedCommand = new SaveChatTurnCommand(
                "질문",
                "답변",
                List.of(
                        new SearchReferenceCandidate(
                                301L,
                                201L,
                                2,
                                0.95
                        ),
                        new SearchReferenceCandidate(
                                301L,
                                202L,
                                0,
                                0.8
                        )
                )
        );
        Mockito.when(
                service.save(
                        10L,
                        2L,
                        expectedCommand
                )
        )
                .thenReturn(
                        new ChatTurnResult(
                                101L,
                                102L
                        )
                );

        // when
        ResponseEntity<ChatTurnResponse> response = controller.save(
                10L,
                request,
                OWNER
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(
                new ChatTurnResponse(
                        101L,
                        102L
                )
        );
    }

    @Test
    @DisplayName("서비스의 채팅 예외를 그대로 전파한다")
    void save_failure_propagatesChatException() {
        // given
        ChatTurnService service = Mockito.mock(ChatTurnService.class);
        ChatTurnController controller = new ChatTurnController(service);
        Mockito.when(
                service.save(
                        Mockito.eq(10L),
                        Mockito.eq(2L),
                        Mockito.any()
                )
        )
                .thenThrow(new ChatException(ChatErrorCode.CHAT_TURN_IN_PROGRESS));

        // when
        ThrowingCallable action = () -> controller.save(
                10L,
                new SaveChatTurnRequest(
                        "질문",
                        "답변",
                        List.of()
                ),
                OWNER
        );

        // then
        assertThatThrownBy(action).isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).chatErrorCode())
                .isEqualTo(ChatErrorCode.CHAT_TURN_IN_PROGRESS);
    }
}
