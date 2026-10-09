package com.knot.backend.document.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.application.DocumentGenerationJobListService;
import com.knot.backend.document.application.DocumentGenerationJobRetryService;
import com.knot.backend.document.application.dto.query.DocumentGenerationJobListParameters;
import com.knot.backend.document.presentation.dto.response.DocumentGenerationJobListResponse;
import com.knot.backend.document.presentation.dto.response.DocumentGenerationJobRetryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/document-generation-jobs")
@RequiredArgsConstructor
public class DocumentGenerationJobController implements DocumentGenerationJobApi {

    private final DocumentGenerationJobListService listService;
    private final DocumentGenerationJobRetryService retryService;

    @Override
    @GetMapping
    public DocumentGenerationJobListResponse findDocumentGenerationJobs(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return DocumentGenerationJobListResponse.from(
                listService.find(
                        workspaceId,
                        authenticatedMember.getMemberId(),
                        DocumentGenerationJobListParameters.of(
                                cursor,
                                size
                        )
                )
        );
    }

    @Override
    @PostMapping("/{jobId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentGenerationJobRetryResponse retryDocumentGenerationJob(
            @PathVariable Long workspaceId,
            @PathVariable Long jobId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return DocumentGenerationJobRetryResponse.from(
                retryService.retry(
                        workspaceId,
                        authenticatedMember.getMemberId(),
                        jobId
                )
        );
    }
}
