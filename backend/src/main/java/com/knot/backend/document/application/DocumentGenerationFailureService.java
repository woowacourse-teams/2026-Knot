package com.knot.backend.document.application;

import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import com.knot.backend.document.domain.DocumentGenerationFailureCause;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import com.knot.backend.document.domain.DocumentGenerationRetryPolicy;
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
public class DocumentGenerationFailureService {

    private final WorkspaceRepository workspaces;
    private final DocumentGenerationJobRepository jobs;
    private final DocumentGenerationBatchRepository batches;
    private final DocumentGenerationRetryPolicy policy;
    private final DocumentGenerationWorkerProperties properties;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void failAttempt(
            long workspaceId,
            long jobId,
            int expectedAttemptCount,
            DocumentGenerationFailureCause cause
    ) {
        failCurrentAttempt(
                workspaceId,
                jobId,
                expectedAttemptCount,
                cause,
                false
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void expireAttempt(
            long workspaceId,
            long jobId,
            int expectedAttemptCount
    ) {
        failCurrentAttempt(
                workspaceId,
                jobId,
                expectedAttemptCount,
                DocumentGenerationFailureCause.EXECUTION_EXPIRED,
                true
        );
    }

    private void failCurrentAttempt(
            long workspaceId,
            long jobId,
            int expectedAttemptCount,
            DocumentGenerationFailureCause cause,
            boolean onlyExpired
    ) {
        Optional<Workspace> workspace = workspaces.findIncludingDeletedByIdForUpdate(workspaceId);
        if (workspace.isEmpty()) {
            return;
        }
        Optional<DocumentGenerationJob> candidate = jobs.findByWorkspaceIdAndIdForUpdate(
                workspaceId,
                jobId
        );
        if (candidate.isEmpty()) {
            return;
        }
        DocumentGenerationJob job = candidate.orElseThrow();
        if (!isCurrentExecution(
                job,
                expectedAttemptCount
        )) {
            return;
        }
        Instant now = clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
        if (onlyExpired && !job.isExpiredAt(now)) {
            return;
        }
        DocumentGenerationFailureCause actualCause = cause;
        if (workspace.orElseThrow()
                .isDeleted()) {
            actualCause = DocumentGenerationFailureCause.WORKSPACE_DELETED;
        } else if (job.isExpiredAt(now)) {
            actualCause = DocumentGenerationFailureCause.EXECUTION_EXPIRED;
        }
        DocumentGenerationBatch batch = batches.findByIdForUpdate(job.getBatchId())
                .orElseThrow();
        DocumentGenerationJobStatus previous = job.getStatus();
        job.recordFailure(
                now,
                actualCause
        );
        if (policy.isAutomaticallyRetryable(actualCause) && job.canRetryAutomatically()) {
            job.retryAutomatically(
                    now,
                    policy.calculateNextAttemptAt(
                            now,
                            properties.getAutomaticRetryBackoff()
                    )
            );
        }
        batch.recordJobTransition(
                job.getStage(),
                previous,
                job.getStatus(),
                now
        );
        jobs.flush();
    }

    private boolean isCurrentExecution(
            DocumentGenerationJob job,
            int expectedAttemptCount
    ) {
        return job.getStatus() == DocumentGenerationJobStatus.RUNNING && job.getAttemptCount() == expectedAttemptCount;
    }
}
