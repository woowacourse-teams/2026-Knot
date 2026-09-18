package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceInvitationService;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceInvitationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "워크스페이스 초대", description = "워크스페이스 초대 코드와 링크 발급, 조회, 재발급")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class WorkspaceInvitationController {
    private final WorkspaceInvitationService workspaceInvitationService;

    public WorkspaceInvitationController(WorkspaceInvitationService workspaceInvitationService) {
        this.workspaceInvitationService = workspaceInvitationService;
    }

    @PostMapping("/invitations")
    public ResponseEntity<WorkspaceInvitationResponse> issue(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        WorkspaceInvitationResult result = workspaceInvitationService.issue(
                workspaceId,
                authenticatedMember.getMemberId()
        );
        ResponseEntity.BodyBuilder responseBuilder = result.created()
                ? ResponseEntity.created(invitationUri(workspaceId))
                : ResponseEntity.ok();
        return responseBuilder.body(WorkspaceInvitationResponse.from(result));
    }

    @GetMapping("/invitation")
    public WorkspaceInvitationResponse get(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return WorkspaceInvitationResponse.from(
                workspaceInvitationService.get(
                        workspaceId,
                        authenticatedMember.getMemberId()
                )
        );
    }

    @PostMapping("/invitations/reissue")
    public ResponseEntity<WorkspaceInvitationResponse> reissue(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        WorkspaceInvitationResult result = workspaceInvitationService.reissue(
                workspaceId,
                authenticatedMember.getMemberId()
        );
        return ResponseEntity.created(invitationUri(workspaceId))
                .body(WorkspaceInvitationResponse.from(result));
    }

    private URI invitationUri(Long workspaceId) {
        return URI.create("/api/v1/workspaces/" + workspaceId + "/invitation");
    }
}
