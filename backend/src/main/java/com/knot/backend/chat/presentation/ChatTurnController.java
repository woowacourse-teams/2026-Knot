package com.knot.backend.chat.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.ChatTurnService;
import com.knot.backend.chat.presentation.dto.request.SaveChatTurnRequest;
import com.knot.backend.chat.presentation.dto.response.ChatTurnResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ChatTurnController implements ChatTurnApi {
    private final ChatTurnService chatTurnService;

    @Override
    @PostMapping(path = "/{sessionId}/turns", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatTurnResponse> save(
            @PathVariable long sessionId,
            @Valid @RequestBody SaveChatTurnRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ChatTurnResponse response = ChatTurnResponse.from(
                chatTurnService.save(
                        sessionId,
                        authenticatedMember.getMemberId(),
                        request.toCommand()
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }
}
