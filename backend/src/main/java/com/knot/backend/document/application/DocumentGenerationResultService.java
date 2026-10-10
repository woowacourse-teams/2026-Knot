package com.knot.backend.document.application;

import com.knot.backend.document.application.dto.result.DocumentGenerationInputResult;
import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.domain.DocumentText;
import com.knot.backend.document.domain.Document;
import com.knot.backend.document.domain.DocumentConfirmation;
import com.knot.backend.document.domain.DocumentConfirmationRepository;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentRepository;
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
        if (job.isSucceeded()) {
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
        Instant completedAt = clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
        Document draft = createDocument(
                workspaceId,
                job,
                input,
                result,
                completedAt
        );
        List<Long> targets = findConfirmationTargets(workspaceId);
        Document document = documents.save(draft);
        saveConfirmationTargets(
                document.getId(),
                targets
        );
        job.recordSuccess(completedAt);
        jobs.flush();
        return document.getId();
    }

    private Document createDocument(
            long workspaceId,
            DocumentGenerationJob job,
            DocumentGenerationInputResult input,
            DocumentGenerationResult result,
            Instant completedAt
    ) {
        try {
            return Document.createDraft(
                    workspaceId,
                    input.recordingSessionId(),
                    input.transcriptId(),
                    job.getId(),
                    job.getTopic(),
                    result.title(),
                    result.summary(),
                    result.content(),
                    completedAt
            );
        } catch (DocumentException exception) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE);
        }
    }

    private void lockWorkspace(long workspaceId) {
        validateIdentifier(workspaceId);
        workspaces.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));
    }

    private Document findCompletedDocument(
            long workspaceId,
            DocumentGenerationJob job
    ) {
        Document document = documents.findByGenerationJobId(job.getId())
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT));
        document.validateGeneratedBy(
                job,
                workspaceId
        );
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
        if (DocumentText.isBlank(input.content())) {
            throw new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT);
        }
        DocumentGenerationBatch batch = batches.findByIdForUpdate(job.getBatchId())
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT));
        batch.validateGenerationTarget(
                job,
                input.recordingSessionId(),
                input.transcriptId()
        );
        return input;
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
    }

    private void validateIdentifier(long identifier) {
        if (identifier <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }
}
