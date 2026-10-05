package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.command.RecordingAudioUploadUrlCommand;
import com.knot.backend.recording.application.dto.result.PresignedAudioUpload;
import com.knot.backend.recording.application.dto.result.RecordingAudioUploadUrlResult;
import com.knot.backend.recording.domain.RecordingAudioUpload;
import com.knot.backend.recording.domain.RecordingAudioUploadRepository;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordingAudioUploadUrlService {
    private static final String STORAGE_KEY_PREFIX = "recordings/";

    private final RecordingWorkspaceAccessValidator workspaceAccessValidator;
    private final RecordingSessionRepository recordingSessionRepository;
    private final RecordingAudioUploadRepository recordingAudioUploadRepository;
    private final RecordingAudioUploadPolicy uploadPolicy;
    private final RecordingAudioStorage audioStorage;
    private final Clock clock;

    @Transactional
    public RecordingAudioUploadUrlResult issue(
            long workspaceId,
            long memberId,
            long recordingId,
            RecordingAudioUploadUrlCommand command
    ) {
        workspaceAccessValidator.validateAndLock(
                workspaceId,
                memberId
        );
        if (recordingId <= 0) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
        RecordingSession session = recordingSessionRepository.findByIdForUpdate(recordingId)
                .orElseThrow(() -> new RecordingException(RecordingErrorCode.RECORDING_NOT_FOUND));
        session.validateControlledBy(
                workspaceId,
                memberId
        );
        session.validateAudioUploadable();
        uploadPolicy.validate(
                command.contentType(),
                command.contentLength()
        );

        Optional<RecordingAudioUpload> existing = recordingAudioUploadRepository.findByRecordingId(recordingId);
        existing.ifPresent(
                reserved -> reserved.changeFile(
                        command.contentType(),
                        command.contentLength()
                )
        );
        RecordingAudioUpload upload = existing.orElseGet(
                () -> reserve(
                        recordingId,
                        command
                )
        );
        RecordingAudioUpload saved = recordingAudioUploadRepository.save(upload);
        PresignedAudioUpload presigned = audioStorage.presignUpload(
                saved.getStorageKey(),
                saved.getContentType(),
                saved.getContentLength()
        );
        return new RecordingAudioUploadUrlResult(
                saved.getId(),
                presigned.uploadUrl(),
                presigned.expiresAt(),
                existing.isEmpty()
        );
    }

    private RecordingAudioUpload reserve(
            long recordingId,
            RecordingAudioUploadUrlCommand command
    ) {
        return RecordingAudioUpload.reserve(
                recordingId,
                STORAGE_KEY_PREFIX + recordingId + "/" + UUID.randomUUID(),
                command.contentType(),
                command.contentLength(),
                clock.instant()
                        .truncatedTo(ChronoUnit.MICROS)
        );
    }
}
