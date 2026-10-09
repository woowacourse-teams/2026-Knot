package com.knot.backend.document.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentRetentionCandidate {

    private final long workspaceId;
    private final long recordingId;
    private final long batchId;
}
