package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceLastViewedService;
import com.knot.backend.workspace.presentation.dto.request.WorkspaceLastViewedUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/last-viewed-workspace")
@RequiredArgsConstructor
public class WorkspaceLastViewedController implements WorkspaceLastViewedApi {
    private final WorkspaceLastViewedService workspaceLastViewedService;

    @Override
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @Valid @RequestBody WorkspaceLastViewedUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        workspaceLastViewedService.update(
                authenticatedMember.getMemberId(),
                request.workspaceId()
        );
    }
}
