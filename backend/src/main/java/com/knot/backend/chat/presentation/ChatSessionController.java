package com.knot.backend.chat.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.ChatSessionService;
import com.knot.backend.chat.application.dto.command.CreateChatSessionCommand;
import com.knot.backend.chat.application.dto.result.ChatSessionResult;
import com.knot.backend.chat.presentation.dto.request.CreateChatSessionRequest;
import com.knot.backend.chat.presentation.dto.response.ChatSessionResponse;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/conversations")
@RequiredArgsConstructor
public class ChatSessionController {
    private final ChatSessionService chatSessionService;

    @PostMapping
    public ResponseEntity<ChatSessionResponse> createSession(
            @PathVariable long workspaceId,
            @Valid @RequestBody CreateChatSessionRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ChatSessionResult result = chatSessionService.createSession(
                new CreateChatSessionCommand(
                        workspaceId,
                        authenticatedMember.getMemberId(),
                        request.title()
                )
        );
        return ResponseEntity.created(URI.create("/api/conversations/" + result.id()))
                .body(ChatSessionResponse.from(result));
    }

    @GetMapping
    public List<ChatSessionResponse> findSessions(
            @PathVariable long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return chatSessionService.findSessions(
                workspaceId,
                authenticatedMember.getMemberId()
        )
                .stream()
                .map(ChatSessionResponse::from)
                .toList();
    }
}
