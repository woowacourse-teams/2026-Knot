package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceQueryService;
import com.knot.backend.workspace.application.dto.result.WorkspaceDetailResult;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceDetailResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
@Tag(name = "워크스페이스", description = "워크스페이스 생성, 조회, 참여")
public class WorkspaceQueryController {
    private final WorkspaceQueryService workspaceQueryService;

    @GetMapping("/{workspaceId}")
    public WorkspaceDetailResponse detail(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        WorkspaceDetailResult result = workspaceQueryService.findDetail(
                workspaceId,
                authenticatedMember.getMemberId()
        );
        return WorkspaceDetailResponse.from(result);
    }
}
