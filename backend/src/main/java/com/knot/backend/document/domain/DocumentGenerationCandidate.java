package com.knot.backend.document.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DocumentGenerationCandidate {

    private final long workspaceId;
    private final long jobId;
    private final int attemptCount;
}
