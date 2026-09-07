package com.knot.backend.chat.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.ChatSearchService;
import com.knot.backend.chat.presentation.dto.request.SearchChatMessageRequest;
import com.knot.backend.chat.presentation.dto.response.ChatSearchResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
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
public class ChatSearchController implements ChatSearchApi {
    private final ChatSearchService chatSearchService;

    @Override
    @PostMapping(path = "/{sessionId}/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatSearchResponse> search(
            @PathVariable long sessionId,
            @Valid @RequestBody SearchChatMessageRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ChatSearchResponse response = ChatSearchResponse.from(
                chatSearchService.search(
                        sessionId,
                        authenticatedMember.getMemberId(),
                        request.content()
                )
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
