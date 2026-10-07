package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationExecutionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentGenerationExecutionRequestJpaRepository
        extends
            JpaRepository<DocumentGenerationExecutionRequest, Long> {}
