package com.knot.backend.chat.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.WorkspaceSearchService;
import com.knot.backend.chat.presentation.dto.request.WorkspaceSearchRequest;
import com.knot.backend.chat.presentation.dto.response.WorkspaceSearchResponse;
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
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceSearchController implements WorkspaceSearchApi {
    private final WorkspaceSearchService workspaceSearchService;

    @Override
    @PostMapping(path = "/{workspaceId}/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkspaceSearchResponse> search(
            @PathVariable long workspaceId,
            @Valid @RequestBody WorkspaceSearchRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        WorkspaceSearchResponse response = WorkspaceSearchResponse.from(
                workspaceSearchService.search(
                        workspaceId,
                        authenticatedMember.getMemberId(),
                        request.content()
                )
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
