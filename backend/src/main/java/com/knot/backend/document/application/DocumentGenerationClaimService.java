package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentGenerationExecution;
import com.knot.backend.document.application.dto.result.DocumentGenerationInputResult;
import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import com.knot.backend.document.domain.DocumentGenerationFailureCause;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentGenerationJobStage;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import com.knot.backend.document.domain.DocumentTopicRegistrationState;
import com.knot.backend.global.config.DocumentGenerationWorkerProperties;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentGenerationClaimService {

    private final WorkspaceRepository workspaces;
    private final DocumentGenerationJobRepository jobs;
    private final DocumentGenerationInputQuery inputs;
    private final DocumentGenerationBatchRepository batches;
    private final DocumentGenerationWorkerProperties properties;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<DocumentGenerationExecution> claim(
            long workspaceId,
            long jobId
    ) {
        Optional<Workspace> workspace = workspaces.findIncludingDeletedByIdForUpdate(workspaceId);
        if (workspace.isEmpty()) {
            return Optional.empty();
        }
        Optional<DocumentGenerationJob> candidate = jobs.findByWorkspaceIdAndIdForUpdate(
                workspaceId,
                jobId
        );
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        DocumentGenerationJob job = candidate.orElseThrow();
        Instant now = clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
        if (!job.isReadyAt(now)) {
            return Optional.empty();
        }
        if (workspace.orElseThrow()
                .isDeleted()) {
            finishInvalidInput(
                    job,
                    DocumentGenerationFailureCause.WORKSPACE_DELETED,
                    now
            );
            return Optional.empty();
        }
        Optional<DocumentGenerationInputResult> input = inputs.findForUpdate(
                workspaceId,
                job.getTranscriptId()
        );
        DocumentGenerationBatch batch = batches.findByIdForUpdate(job.getBatchId())
                .orElseThrow();
        if (input.isEmpty() || !hasUsableInput(
                job,
                batch,
                input.orElseThrow()
        )) {
            finishInvalidInput(
                    job,
                    DocumentGenerationFailureCause.INVALID_INPUT,
                    now
            );
            return Optional.empty();
        }
        job.startRunning(
                now,
                now.plus(properties.getExecutionLease())
        );
        batch.recordJobTransition(
                job.getStage(),
                DocumentGenerationJobStatus.QUEUED,
                job.getStatus(),
                now
        );
        jobs.flush();
        return Optional.of(
                new DocumentGenerationExecution(
                        workspaceId,
                        jobId,
                        job.getAttemptCount(),
                        job.getStage(),
                        input.orElseThrow()
                                .content(),
                        job.getTopic()
                )
        );
    }

    private boolean hasUsableInput(
            DocumentGenerationJob job,
            DocumentGenerationBatch batch,
            DocumentGenerationInputResult stored
    ) {
        if (stored.content() == null || stored.content()
                .codePoints()
                .allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            return false;
        }
        if (!Long.valueOf(stored.transcriptId())
                .equals(batch.getTranscriptId()) || stored.recordingSessionId() != batch.getRecordingSessionId()) {
            return false;
        }
        if (job.getStage() == DocumentGenerationJobStage.CLASSIFICATION) {
            return batch.getTopicRegistrationState() == DocumentTopicRegistrationState.WAITING_CLASSIFICATION;
        }
        return batch.getTopicRegistrationState() == DocumentTopicRegistrationState.TOPICS_REGISTERED
                && batch.getTopics()
                        .contains(job.getTopic());
    }

    private void finishInvalidInput(
            DocumentGenerationJob job,
            DocumentGenerationFailureCause cause,
            Instant now
    ) {
        DocumentGenerationBatch batch = batches.findByIdForUpdate(job.getBatchId())
                .orElseThrow();
        DocumentGenerationJobStatus previous = job.getStatus();
        job.recordFailure(
                now,
                cause
        );
        batch.recordJobTransition(
                job.getStage(),
                previous,
                job.getStatus(),
                now
        );
        jobs.flush();
    }
}
