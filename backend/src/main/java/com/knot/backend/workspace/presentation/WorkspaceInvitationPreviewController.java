package com.knot.backend.workspace.presentation;

import com.knot.backend.workspace.application.WorkspaceInvitationService;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceInvitationPreviewResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/invitations")
public class WorkspaceInvitationPreviewController implements WorkspaceInvitationPreviewApi {
    private final WorkspaceInvitationService workspaceInvitationService;

    public WorkspaceInvitationPreviewController(WorkspaceInvitationService workspaceInvitationService) {
        this.workspaceInvitationService = workspaceInvitationService;
    }

    @Override
    @GetMapping("/{tokenOrCode}")
    public WorkspaceInvitationPreviewResponse preview(
            @PathVariable String tokenOrCode,
            HttpServletRequest request
    ) {
        return WorkspaceInvitationPreviewResponse.from(
                workspaceInvitationService.preview(
                        tokenOrCode,
                        request.getRemoteAddr()
                )
        );
    }
}
