package com.knot.backend.document.domain;

public interface DocumentGenerationExecutionRequestRepository {

    DocumentGenerationExecutionRequest save(DocumentGenerationExecutionRequest request);

    void flush();
}
