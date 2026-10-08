package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentTranscriptSnapshot;
import java.util.Optional;

public interface DocumentTranscriptQuery {

    Optional<DocumentTranscriptSnapshot> find(
            long workspaceId,
            long documentId
    );
}
