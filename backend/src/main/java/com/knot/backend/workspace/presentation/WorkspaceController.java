package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceService;
import com.knot.backend.workspace.application.dto.result.WorkspaceCreateResult;
import com.knot.backend.workspace.presentation.dto.request.WorkspaceCreateRequest;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<WorkspaceCreateResponse> create(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody WorkspaceCreateRequest request
    ) {
        WorkspaceCreateResult result = workspaceService.create(
                authenticatedMember.getMemberId(),
                request.name()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WorkspaceCreateResponse.from(result));
    }
}
