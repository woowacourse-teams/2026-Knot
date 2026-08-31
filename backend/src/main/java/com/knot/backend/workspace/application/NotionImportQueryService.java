package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.NotionImportStatusResult;
import com.knot.backend.workspace.domain.NotionErrorCode;
import com.knot.backend.workspace.domain.NotionException;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotionImportQueryService {
    private final NotionImportRunRepository importRunRepository;

    public NotionImportStatusResult findStatus(
            Long importRunId,
            long memberId
    ) {
        validateImportRunId(importRunId);
        NotionImportRun importRun = importRunRepository.findVisibleByIdAndMemberId(
                importRunId,
                memberId
        )
                .orElseThrow(() -> new NotionException(NotionErrorCode.NOTION_IMPORT_RUN_NOT_FOUND));
        return NotionImportStatusResult.from(importRun);
    }

    private void validateImportRunId(Long importRunId) {
        if (importRunId == null || importRunId <= 0) {
            throw new NotionException(NotionErrorCode.INVALID_NOTION_IMPORT_RUN_ID);
        }
    }
}
