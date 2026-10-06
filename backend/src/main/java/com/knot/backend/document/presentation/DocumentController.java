package com.knot.backend.document.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.application.DocumentDetailService;
import com.knot.backend.document.application.DocumentListService;
import com.knot.backend.document.application.dto.query.DocumentListParameters;
import com.knot.backend.document.domain.MyConfirmationState;
import com.knot.backend.document.presentation.dto.response.DocumentDetailResponse;
import com.knot.backend.document.presentation.dto.response.DocumentListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/documents")
@RequiredArgsConstructor
public class DocumentController implements DocumentApi {
    private final DocumentDetailService detailService;
    private final DocumentListService listService;

    @Override
    @GetMapping
    public DocumentListResponse findDocuments(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) MyConfirmationState myConfirmation,
            @RequestParam(required = false) Long recordingSessionId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return DocumentListResponse.from(
                listService.find(
                        workspaceId,
                        authenticatedMember.getMemberId(),
                        DocumentListParameters.of(
                                cursor,
                                size,
                                myConfirmation,
                                recordingSessionId
                        )
                )
        );
    }

    @Override
    @GetMapping("/{documentId}")
    public DocumentDetailResponse findDocument(
            @PathVariable Long workspaceId,
            @PathVariable Long documentId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return DocumentDetailResponse.from(
                detailService.find(
                        workspaceId,
                        authenticatedMember.getMemberId(),
                        documentId
                )
        );
    }
}
