package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.ContentImportQueryService;
import com.knot.backend.workspace.application.dto.result.ContentImportStatusResult;
import com.knot.backend.workspace.domain.ContentImportException;
import com.knot.backend.workspace.presentation.dto.response.NotionImportStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class NotionImportController implements NotionImportApi {
    private final ContentImportQueryService importQueryService;

    @Override
    @GetMapping(value = "/{importRunId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NotionImportStatusResponse> status(
            @PathVariable Long importRunId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        ContentImportStatusResult result;
        try {
            result = importQueryService.findStatus(
                    importRunId,
                    authenticatedMember.getMemberId()
            );
        } catch (ContentImportException exception) {
            throw NotionImportException.from(exception);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(NotionImportStatusResponse.from(result));
    }
}
