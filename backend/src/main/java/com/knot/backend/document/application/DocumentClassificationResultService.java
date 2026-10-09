package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentGenerationRegistrationResult;
import com.knot.backend.document.application.dto.result.DocumentTopicClassificationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import com.knot.backend.document.domain.DocumentTopicRegistrationState;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentClassificationResultService {

    private final WorkspaceRepository workspaces;
    private final DocumentGenerationJobRepository jobs;
    private final DocumentGenerationInputQuery inputs;
    private final DocumentGenerationBatchRepository batches;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DocumentGenerationRegistrationResult completeClassification(
            long workspaceId,
            long jobId,
            int expectedAttemptCount,
            DocumentTopicClassificationResult classification
    ) {
        validateClassification(classification);
        DocumentGenerationJob job = lockClassification(
                workspaceId,
                jobId,
                expectedAttemptCount
        );
        DocumentGenerationBatch batch = batches.findByIdForUpdate(job.getBatchId())
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT));
        if (job.getStatus() == DocumentGenerationJobStatus.SUCCEEDED) {
            validateRepeatedResult(
                    batch,
                    classification
            );
            return result(batch);
        }
        Instant completedAt = clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
        job.validateRunningAttemptAt(
                expectedAttemptCount,
                completedAt
        );
        batch.registerTopics(
                classification.topics(),
                completedAt
        );
        // 생성 Job의 주제 FK가 같은 트랜잭션에 저장한 주제를 참조하도록 먼저 flush한다.
        batches.saveAndFlush(batch);
        for (String topic : batch.getTopics()) {
            jobs.save(
                    DocumentGenerationJob.queueGeneration(
                            batch.getId(),
                            job.getTranscriptId(),
                            topic,
                            completedAt
                    )
            );
        }
        job.recordSuccess(completedAt);
        batch.recordJobTransition(
                job.getStage(),
                DocumentGenerationJobStatus.RUNNING,
                job.getStatus(),
                completedAt
        );
        jobs.flush();
        return result(batch);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void failClassification(
            long workspaceId,
            long jobId,
            int expectedAttemptCount
    ) {
        DocumentGenerationJob job = lockClassification(
                workspaceId,
                jobId,
                expectedAttemptCount
        );
        if (job.getStatus() == DocumentGenerationJobStatus.FAILED) {
            return;
        }
        job.validateRunningAttempt(expectedAttemptCount);
        Instant failedAt = clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
        DocumentGenerationBatch batch = batches.findByIdForUpdate(job.getBatchId())
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT));
        job.recordFailure(failedAt);
        batch.recordJobTransition(
                job.getStage(),
                DocumentGenerationJobStatus.RUNNING,
                job.getStatus(),
                failedAt
        );
        jobs.flush();
    }

    private DocumentGenerationJob lockClassification(
            long workspaceId,
            long jobId,
            int expectedAttemptCount
    ) {
        validateIdentifier(workspaceId);
        validateIdentifier(jobId);
        workspaces.findIncludingDeletedByIdForUpdate(workspaceId)
                .filter(workspace -> !workspace.isDeleted())
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));
        DocumentGenerationJob job = jobs.findByWorkspaceIdAndIdForUpdate(
                workspaceId,
                jobId
        )
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.DOCUMENT_GENERATION_JOB_NOT_FOUND));
        job.validateClassificationStage();
        job.validateAttempt(expectedAttemptCount);
        inputs.findForUpdate(
                workspaceId,
                job.getTranscriptId()
        )
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.TRANSCRIPT_NOT_FOUND));
        return job;
    }

    private void validateRepeatedResult(
            DocumentGenerationBatch batch,
            DocumentTopicClassificationResult classification
    ) {
        if (batch.getTopicRegistrationState() == DocumentTopicRegistrationState.WAITING_CLASSIFICATION) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
        if (!batch.getTopics()
                .equals(classification.topics())) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
    }

    private void validateClassification(DocumentTopicClassificationResult classification) {
        if (classification == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_RESPONSE);
        }
    }

    private void validateIdentifier(long identifier) {
        if (identifier <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }

    private DocumentGenerationRegistrationResult result(DocumentGenerationBatch batch) {
        return DocumentGenerationRegistrationResult.from(
                batch,
                jobs.findAllByBatchId(batch.getId())
        );
    }
}
