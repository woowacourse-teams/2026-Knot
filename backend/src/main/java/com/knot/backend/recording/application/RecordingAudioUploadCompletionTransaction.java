package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.result.PendingAudioUpload;
import com.knot.backend.recording.application.dto.result.RecordingAudioUploadCompletionResult;
import com.knot.backend.recording.application.dto.result.StoredAudioObject;
import com.knot.backend.recording.domain.RecordingAudioUpload;
import com.knot.backend.recording.domain.RecordingAudioUploadRepository;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordingAudioUploadCompletionTransaction {
    private final RecordingWorkspaceAccessValidator workspaceAccessValidator;
    private final RecordingSessionRepository recordingSessionRepository;
    private final RecordingAudioUploadRepository recordingAudioUploadRepository;
    private final Clock clock;

    @Transactional
    public PendingAudioUpload prepare(
            long workspaceId,
            long memberId,
            long recordingId,
            long uploadId
    ) {
        RecordingAudioUpload upload = findValidatedUpload(
                workspaceId,
                memberId,
                recordingId,
                uploadId
        );
        return new PendingAudioUpload(
                upload.getStorageKey(),
                upload.isCompleted()
        );
    }

    @Transactional
    public RecordingAudioUploadCompletionResult complete(
            long workspaceId,
            long memberId,
            long recordingId,
            long uploadId,
            StoredAudioObject storedObject
    ) {
        RecordingAudioUpload upload = findValidatedUpload(
                workspaceId,
                memberId,
                recordingId,
                uploadId
        );
        if (upload.isCompleted()) {
            return RecordingAudioUploadCompletionResult.from(upload);
        }
        if (!storedObject.exists()) {
            throw new RecordingException(RecordingErrorCode.AUDIO_UPLOAD_NOT_COMPLETED);
        }
        upload.complete(
                storedObject.contentLength(),
                storedObject.contentType(),
                clock.instant()
                        .truncatedTo(ChronoUnit.MICROS)
        );
        return RecordingAudioUploadCompletionResult.from(recordingAudioUploadRepository.save(upload));
    }

    private RecordingAudioUpload findValidatedUpload(
            long workspaceId,
            long memberId,
            long recordingId,
            long uploadId
    ) {
        workspaceAccessValidator.validateAndLock(
                workspaceId,
                memberId
        );
        if (recordingId <= 0 || uploadId <= 0) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
        RecordingSession session = recordingSessionRepository.findByIdForUpdate(recordingId)
                .orElseThrow(() -> new RecordingException(RecordingErrorCode.RECORDING_NOT_FOUND));
        session.validateControlledBy(
                workspaceId,
                memberId
        );
        session.validateAudioUploadable();
        RecordingAudioUpload upload = recordingAudioUploadRepository.findById(uploadId)
                .orElseThrow(() -> new RecordingException(RecordingErrorCode.AUDIO_UPLOAD_NOT_FOUND));
        upload.validateBelongsTo(recordingId);
        return upload;
    }
}
