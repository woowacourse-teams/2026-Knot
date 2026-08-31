package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceService;
import com.knot.backend.workspace.application.dto.result.WorkspaceCreateResult;
import com.knot.backend.workspace.presentation.dto.request.WorkspaceCreateRequest;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceCreateResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "워크스페이스", description = "워크스페이스 생성 및 조회")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
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
