package com.knot.backend.chat.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.ChatSessionService;
import com.knot.backend.chat.presentation.dto.response.ChatMessageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ChatMessageQueryController {
    private final ChatSessionService chatSessionService;

    @GetMapping("/{sessionId}")
    public List<ChatMessageResponse> findMessages(
            @PathVariable long sessionId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return chatSessionService.findMessages(
                sessionId,
                authenticatedMember.getMemberId()
        )
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }
}
