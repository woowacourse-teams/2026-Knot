package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentGenerationBatch;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface DocumentGenerationBatchJpaRepository extends JpaRepository<DocumentGenerationBatch, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentGenerationBatch> findByRecordingSessionId(long recordingSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM DocumentGenerationBatch b WHERE b.id = :batchId")
    Optional<DocumentGenerationBatch> findLockedById(long batchId);
}
