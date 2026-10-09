package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentGenerationProgressResult;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentGenerationProgressService {

    private final DocumentGenerationBatchRepository batches;

    @Transactional(readOnly = true)
    public Optional<DocumentGenerationProgressResult> findProgress(
            long workspaceId,
            long recordingSessionId
    ) {
        return batches.findByWorkspaceIdAndRecordingSessionId(
                workspaceId,
                recordingSessionId
        )
                .map(DocumentGenerationProgressResult::from);
    }
}
