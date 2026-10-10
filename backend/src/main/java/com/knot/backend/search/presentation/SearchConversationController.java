package com.knot.backend.search.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.search.application.SearchConversationListService;
import com.knot.backend.search.application.SearchMessageListService;
import com.knot.backend.search.application.dto.query.SearchConversationListParameters;
import com.knot.backend.search.application.dto.query.SearchMessageListParameters;
import com.knot.backend.search.presentation.dto.response.SearchConversationListResponse;
import com.knot.backend.search.presentation.dto.response.SearchMessageListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/search/conversations")
@RequiredArgsConstructor
public class SearchConversationController implements SearchConversationApi {

    private final SearchConversationListService service;
    private final SearchMessageListService messageService;

    @Override
    @GetMapping
    public SearchConversationListResponse findConversations(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return SearchConversationListResponse.from(
                service.find(
                        workspaceId,
                        authenticatedMember.getMemberId(),
                        SearchConversationListParameters.of(
                                cursor,
                                size
                        )
                )
        );
    }

    @Override
    @GetMapping("/{conversationId}/messages")
    public SearchMessageListResponse findMessages(
            @PathVariable Long workspaceId,
            @PathVariable Long conversationId,
            @RequestParam(required = false) Integer beforeSequence,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return SearchMessageListResponse.from(
                messageService.find(
                        workspaceId,
                        authenticatedMember.getMemberId(),
                        conversationId,
                        SearchMessageListParameters.of(
                                beforeSequence,
                                size
                        )
                )
        );
    }
}
