package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceOwnershipTransferService;
import com.knot.backend.workspace.presentation.dto.request.WorkspaceOwnershipTransferRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceOwnershipTransferController implements WorkspaceOwnershipTransferApi {
    private final WorkspaceOwnershipTransferService workspaceOwnershipTransferService;

    @Override
    @PostMapping(value = "/{workspaceId}/ownership-transfers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferOwnership(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceOwnershipTransferRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        workspaceOwnershipTransferService.transferOwnership(
                authenticatedMember.getMemberId(),
                workspaceId,
                request.successorMemberId()
        );
    }
}
