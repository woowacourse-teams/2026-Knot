package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentDetailSnapshot;
import java.util.Optional;

public interface DocumentDetailQuery {

    Optional<DocumentDetailSnapshot> find(
            long workspaceId,
            long documentId,
            long memberId
    );
}
