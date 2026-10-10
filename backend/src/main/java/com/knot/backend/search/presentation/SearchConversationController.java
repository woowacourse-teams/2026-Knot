package com.knot.backend.search.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.search.application.SearchConversationListService;
import com.knot.backend.search.application.dto.query.SearchConversationListParameters;
import com.knot.backend.search.presentation.dto.response.SearchConversationListResponse;
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
}
