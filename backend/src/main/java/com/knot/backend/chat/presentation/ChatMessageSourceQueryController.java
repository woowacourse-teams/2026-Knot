package com.knot.backend.chat.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.ChatMessageSourceQueryService;
import com.knot.backend.chat.presentation.dto.response.SearchReferencesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class ChatMessageSourceQueryController implements ChatMessageSourceQueryApi {
    private final ChatMessageSourceQueryService chatMessageSourceQueryService;

    @Override
    @GetMapping(value = "/{messageId}/sources", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchReferencesResponse> findSources(
            @PathVariable Long messageId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        SearchReferencesResponse response = SearchReferencesResponse.from(
                chatMessageSourceQueryService.findSources(
                        messageId,
                        authenticatedMember.getMemberId()
                )
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
