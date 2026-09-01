package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.NotionPageTreeQueryService;
import com.knot.backend.workspace.presentation.dto.response.NotionPageTreeItemResponse;
import java.util.List;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/notion-pages")
@RequiredArgsConstructor
public class NotionPageTreeController implements NotionPageTreeApi {
    private final NotionPageTreeQueryService pageTreeQueryService;

    @Override
    @GetMapping(value = "/tree", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<NotionPageTreeItemResponse>> tree(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        List<NotionPageTreeItemResponse> response = pageTreeQueryService.findTree(
                workspaceId,
                authenticatedMember.getMemberId()
        )
                .stream()
                .map(NotionPageTreeItemResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
