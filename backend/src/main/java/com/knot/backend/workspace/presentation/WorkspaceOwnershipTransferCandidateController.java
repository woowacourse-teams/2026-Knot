package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceOwnershipTransferCandidateService;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceOwnershipTransferCandidateResponse;
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
public class WorkspaceOwnershipTransferCandidateController implements WorkspaceOwnershipTransferCandidateApi {
    private final WorkspaceOwnershipTransferCandidateService candidateService;

    @Override
    @GetMapping("/{workspaceId}/ownership-transfer-candidates")
    public List<WorkspaceOwnershipTransferCandidateResponse> findCandidates(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return candidateService.findCandidates(
                workspaceId,
                authenticatedMember.getMemberId()
        )
                .stream()
                .map(WorkspaceOwnershipTransferCandidateResponse::from)
                .toList();
    }
}
