package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.ContentImportCommandService;
import com.knot.backend.workspace.application.dto.result.ContentImportRunRequestResult;
import com.knot.backend.workspace.domain.ContentImportException;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.presentation.dto.response.NotionImportStartResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/imports")
@RequiredArgsConstructor
public class NotionImportStartController implements NotionImportStartApi {
    private final ContentImportCommandService importCommandService;

    @Override
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NotionImportStartResponse> start(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ContentImportRunRequestResult result;
        try {
            result = importCommandService.start(
                    workspaceId,
                    authenticatedMember.getMemberId(),
                    ContentSourceProvider.NOTION
            );
        } catch (ContentImportException exception) {
            throw NotionImportException.from(exception);
        }
        ResponseEntity.BodyBuilder responseBuilder = result.created()
                ? ResponseEntity.accepted()
                : ResponseEntity.status(HttpStatus.CONFLICT);
        return responseBuilder.location(importUri(result.id()))
                .body(new NotionImportStartResponse(result.id()));
    }

    private URI importUri(long importRunId) {
        return URI.create("/api/v1/imports/" + importRunId);
    }
}
