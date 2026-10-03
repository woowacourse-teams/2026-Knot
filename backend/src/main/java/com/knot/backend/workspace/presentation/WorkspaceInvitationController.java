package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceInvitationService;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceInvitationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "워크스페이스 초대", description = "워크스페이스 초대 코드와 링크 발급")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class WorkspaceInvitationController {
    private final WorkspaceInvitationService workspaceInvitationService;

    public WorkspaceInvitationController(WorkspaceInvitationService workspaceInvitationService) {
        this.workspaceInvitationService = workspaceInvitationService;
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceInvitationResponse issue(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return WorkspaceInvitationResponse.from(
                workspaceInvitationService.issue(
                        workspaceId,
                        authenticatedMember.getMemberId()
                )
        );
    }
}
