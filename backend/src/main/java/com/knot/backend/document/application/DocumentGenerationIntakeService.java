package com.knot.backend.document.application;

import com.knot.backend.document.domain.DocumentText;
import com.knot.backend.document.application.dto.result.DocumentGenerationInputResult;
import com.knot.backend.document.application.dto.result.DocumentGenerationRegistrationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationBatch;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentGenerationIntakeService {

    private final WorkspaceRepository workspaces;
    private final RecordingSessionRepository recordings;
    private final DocumentGenerationInputQuery inputs;
    private final DocumentGenerationBatchRepository batches;
    private final DocumentGenerationJobRepository jobs;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentGenerationRegistrationResult acceptCompletedTranscript(
            long workspaceId,
            long recordingSessionId,
            long transcriptId
    ) {
        validateIdentifier(workspaceId);
        validateIdentifier(recordingSessionId);
        validateIdentifier(transcriptId);
        workspaces.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));
        validateRecording(
                workspaceId,
                recordingSessionId
        );
        Optional<DocumentGenerationBatch> existing = batches.findByRecordingSessionIdForUpdate(recordingSessionId);
        if (existing.isPresent()) {
            DocumentGenerationBatch batch = existing.orElseThrow();
            batch.validateInput(transcriptId);
            return result(batch);
        }
        DocumentGenerationInputResult input = inputs.findForUpdate(
                workspaceId,
                transcriptId
        )
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.TRANSCRIPT_NOT_FOUND));
        validateRecordingInput(
                recordingSessionId,
                input
        );
        Instant acceptedAt = clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                recordingSessionId,
                transcriptId,
                acceptedAt
        );
        if (isEmptyContent(input.content())) {
            batch.registerTopics(
                    List.of(),
                    acceptedAt
            );
            batches.saveAndFlush(batch);
            return result(batch);
        }
        batches.saveAndFlush(batch);
        jobs.save(
                DocumentGenerationJob.queueClassification(
                        batch.getId(),
                        transcriptId,
                        acceptedAt
                )
        );
        jobs.flush();
        return result(batch);
    }

    private void validateRecording(
            long workspaceId,
            long recordingSessionId
    ) {
        RecordingSession recording = recordings.findByIdForUpdate(recordingSessionId)
                .orElseThrow(() -> new DocumentException(DocumentErrorCode.TRANSCRIPT_NOT_FOUND));
        if (!recording.belongsTo(workspaceId)) {
            throw new DocumentException(DocumentErrorCode.TRANSCRIPT_NOT_FOUND);
        }
        recording.validateEnded();
    }

    private void validateRecordingInput(
            long recordingSessionId,
            DocumentGenerationInputResult input
    ) {
        if (input.recordingSessionId() != recordingSessionId || input.content() == null) {
            throw new DocumentException(DocumentErrorCode.INVALID_TOPIC_CLASSIFICATION_INPUT);
        }
    }

    private boolean isEmptyContent(String content) {
        return DocumentText.isBlank(content);
    }

    private DocumentGenerationRegistrationResult result(DocumentGenerationBatch batch) {
        return DocumentGenerationRegistrationResult.from(
                batch,
                jobs.findAllByBatchId(batch.getId())
        );
    }

    private void validateIdentifier(long identifier) {
        if (identifier <= 0) {
            throw new DocumentException(DocumentErrorCode.INVALID_PARAMETER);
        }
    }
}
