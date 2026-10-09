package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentGenerationInputResult;
import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.domain.Document;
import com.knot.backend.document.domain.DocumentConfirmation;
import com.knot.backend.document.domain.DocumentConfirmationRepository;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import com.knot.backend.document.domain.DocumentRepository;
import com.knot.backend.document.domain.DocumentTopicRegistrationState;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentGenerationResultService {

    private final WorkspaceRepository workspaces;
    private final DocumentGenerationJobRepository jobs;
    private final DocumentGenerationInputQuery inputs;
    private final DocumentGenerationBatchRepository batches;
    private final WorkspaceMemberRepository members;
    private final DocumentRepository documents;
    private final DocumentConfirmationRepository confirmations;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long completeGeneration(
            long workspaceId,
            long jobId,
            int expectedAttemptCount,
            DocumentGenerationResult result
    ) {
        lockWorkspace(workspaceId);
        DocumentGenerationJob job = lockGenerationJob(
                workspaceId,
                jobId,
                expectedAttemptCount
        );
        if (job.getStatus() == DocumentGenerationJobStatus.SUCCEEDED) {
            return findCompletedDocument(
                    workspaceId,
                    job
            ).getId();
        }
        job.validateRunningAttempt(expectedAttemptCount);
        DocumentGenerationInputResult input = lockRegisteredInput(
                workspaceId,
                job
        );
        validateResult(result);
        List<Long> targets = findConfirmationTargets(workspaceId);
        Instant completedAt = clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
        job.validateRunningAttemptAt(
                expectedAttemptCount,
                completedAt
        );
        Document document = documents.save(
                Document.createDraft(
                        workspaceId,
                        input.recordingSessionId(),
                        input.transcriptId(),
                        job.getId(),
                        job.getTopic(),
                        result.title(),
                        result.summary(),
                        result.content(),
                        completedAt
                )
        );
        saveConfirmationTargets(
                document.getId(),
                targets
        );
        job.recordSuccess(completedAt);
        DocumentGenerationBatch batch = batches.findByIdForUpdate(job.getBatchId())
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT));
        batch.recordJobTransition(
                job.getStage(),
                DocumentGenerationJobStatus.RUNNING,
                job.getStatus(),
                completedAt
        );
        jobs.flush();
        return document.getId();
    }

    private void lockWorkspace(long workspaceId) {
        validateIdentifier(workspaceId);
        workspaces.findIncludingDeletedByIdForUpdate(workspaceId)
                .filter(workspace -> !workspace.isDeleted())
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));
    }

    private Document findCompletedDocument(
            long workspaceId,
            DocumentGenerationJob job
    ) {
        Document document = documents.findByGenerationJobId(job.getId())
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT));
        if (document.getWorkspaceId() != workspaceId || document.getSourceTranscriptId() != job.getTranscriptId()) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
        if (!document.getTopic()
                .equals(job.getTopic())) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
        return document;
    }

    private DocumentGenerationJob lockGenerationJob(
            long workspaceId,
            long jobId,
            int expectedAttemptCount
    ) {
        validateIdentifier(jobId);
        DocumentGenerationJob job = jobs.findByWorkspaceIdAndIdForUpdate(
                workspaceId,
                jobId
        )
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.DOCUMENT_GENERATION_JOB_NOT_FOUND));
        job.validateGenerationStage();
        job.validateAttempt(expectedAttemptCount);
        return job;
    }

    private DocumentGenerationInputResult lockRegisteredInput(
            long workspaceId,
            DocumentGenerationJob job
    ) {
        DocumentGenerationInputResult input = inputs.findForUpdate(
                workspaceId,
                job.getTranscriptId()
        )
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.TRANSCRIPT_NOT_FOUND));
        if (isBlank(input.content())) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT);
        }
        DocumentGenerationBatch batch = batches.findByIdForUpdate(job.getBatchId())
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT));
        validateRegisteredTarget(
                batch,
                job,
                input
        );
        return input;
    }

    private void validateRegisteredTarget(
            DocumentGenerationBatch batch,
            DocumentGenerationJob job,
            DocumentGenerationInputResult input
    ) {
        if (batch.getTopicRegistrationState() != DocumentTopicRegistrationState.TOPICS_REGISTERED) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
        if (!Long.valueOf(input.transcriptId())
                .equals(batch.getTranscriptId()) || input.recordingSessionId() != batch.getRecordingSessionId()) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
        if (!batch.getTopics()
                .contains(job.getTopic())) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
    }

    private List<Long> findConfirmationTargets(long workspaceId) {
        List<Long> targets = members.findActiveMemberIdsByWorkspaceId(workspaceId);
        if (targets.isEmpty()) {
            throw new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT);
        }
        return targets;
    }

    private void saveConfirmationTargets(
            long documentId,
            List<Long> targets
    ) {
        confirmations.saveAll(
                targets.stream()
                        .map(
                                memberId -> DocumentConfirmation.require(
                                        documentId,
                                        memberId
                                )
                        )
                        .toList()
        );
    }

    private void validateResult(DocumentGenerationResult result) {
        if (result == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE);
        }
        validateRequiredResultText(result.title());
        validateRequiredResultText(result.content());
    }

    private void validateRequiredResultText(String value) {
        if (isBlank(value)) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.codePoints()
                .allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
    }

    private void validateIdentifier(long identifier) {
        if (identifier <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }
}
