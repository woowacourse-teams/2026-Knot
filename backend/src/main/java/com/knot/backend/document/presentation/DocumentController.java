package com.knot.backend.document.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.application.DocumentDetailService;
import com.knot.backend.document.presentation.dto.response.DocumentDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/documents")
@RequiredArgsConstructor
public class DocumentController implements DocumentApi {
    private final DocumentDetailService detailService;

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
