package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentGenerationWorker;
import com.knot.backend.document.application.DocumentGenerationRecoveryService;
import com.knot.backend.document.domain.DocumentGenerationCandidate;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.global.config.DocumentGenerationWorkerProperties;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "knot.document-generation.worker", name = "enabled", havingValue = "true")
public class DocumentGenerationScheduler {

    private final DocumentGenerationJobRepository jobs;
    private final DocumentGenerationWorker worker;
    private final DocumentGenerationRecoveryService recovery;
    private final DocumentGenerationWorkerProperties properties;
    private final ThreadPoolTaskExecutor executor;
    private final Clock clock;

    public DocumentGenerationScheduler(
            DocumentGenerationJobRepository jobs,
            DocumentGenerationWorker worker,
            DocumentGenerationRecoveryService recovery,
            DocumentGenerationWorkerProperties properties,
            @Qualifier("documentGenerationExecutor") ThreadPoolTaskExecutor executor,
            Clock clock
    ) {
        this.jobs = jobs;
        this.worker = worker;
        this.recovery = recovery;
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${knot.document-generation.worker.poll-interval:1s}")
    public void dispatchQueued() {
        for (DocumentGenerationCandidate candidate : jobs.findReadyCandidates(
                clock.instant(),
                properties.getCandidateLimit()
        )) {
            try {
                executor.execute(() -> execute(candidate));
            } catch (TaskRejectedException exception) {
                return;
            }
        }
    }

    @Scheduled(fixedDelayString = "${knot.document-generation.worker.recovery-interval:5s}")
    public void recoverExpired() {
        for (DocumentGenerationCandidate candidate : jobs.findExpiredCandidates(
                clock.instant(),
                properties.getCandidateLimit()
        )) {
            try {
                recovery.recoverExpired(
                        candidate.getWorkspaceId(),
                        candidate.getJobId(),
                        candidate.getAttemptCount()
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Document generation recovery deferred jobId={} attempt={}",
                        candidate.getJobId(),
                        candidate.getAttemptCount()
                );
            }
        }
    }

    private void execute(DocumentGenerationCandidate candidate) {
        try {
            worker.execute(
                    candidate.getWorkspaceId(),
                    candidate.getJobId()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Document generation execution deferred jobId={} attempt={}",
                    candidate.getJobId(),
                    candidate.getAttemptCount()
            );
        }
    }
}
