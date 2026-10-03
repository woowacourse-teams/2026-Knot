package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceInvitationQueryService;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceInvitationSummaryResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceInvitationQueryController implements WorkspaceInvitationQueryApi {
    private final WorkspaceInvitationQueryService queryService;

    @Override
    @GetMapping("/{workspaceId}/invitations")
    public List<WorkspaceInvitationSummaryResponse> findAll(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return queryService.findAll(
                workspaceId,
                authenticatedMember.getMemberId()
        )
                .stream()
                .map(WorkspaceInvitationSummaryResponse::from)
                .toList();
    }
}
