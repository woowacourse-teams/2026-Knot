package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ContentImportStatusResult;
import com.knot.backend.workspace.domain.ContentImportErrorCode;
import com.knot.backend.workspace.domain.ContentImportException;
import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentImportQueryService {
    private final ContentImportRunRepository importRunRepository;

    public ContentImportStatusResult findStatus(
            Long importRunId,
            long memberId
    ) {
        validateImportRunId(importRunId);
        ContentImportRun importRun = importRunRepository.findVisibleByIdAndMemberId(
                importRunId,
                memberId
        )
                .orElseThrow(() -> new ContentImportException(ContentImportErrorCode.CONTENT_IMPORT_RUN_NOT_FOUND));
        return ContentImportStatusResult.from(importRun);
    }

    private void validateImportRunId(Long importRunId) {
        if (importRunId == null || importRunId <= 0) {
            throw new ContentImportException(ContentImportErrorCode.INVALID_CONTENT_IMPORT_RUN_ID);
        }
    }
}
