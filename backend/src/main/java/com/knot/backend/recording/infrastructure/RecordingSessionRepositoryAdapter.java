package com.knot.backend.recording.infrastructure;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;

@Repository
@RequiredArgsConstructor
public class RecordingSessionRepositoryAdapter implements RecordingSessionRepository {
    private final RecordingSessionJpaRepository recordingSessionJpaRepository;

    @Override
    public RecordingSession save(RecordingSession recordingSession) {
        try {
            return recordingSessionJpaRepository.saveAndFlush(recordingSession);
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityViolation(exception);
        }
    }

    @Override
    public Optional<RecordingSession> findById(Long recordingSessionId) {
        return recordingSessionJpaRepository.findById(recordingSessionId);
    }

    @Override
    public Optional<RecordingSession> findByMemberIdAndRequestId(
            long memberId,
            UUID requestId
    ) {
        return recordingSessionJpaRepository.findByMemberIdAndRequestId(
                memberId,
                requestId
        );
    }

    @Override
    public boolean existsActiveByMemberId(long memberId) {
        return recordingSessionJpaRepository.existsActiveByMemberId(memberId);
    }

    private RecordingException translateIntegrityViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return new RecordingException(switch (String.valueOf(violation.getConstraintName())) {
                    case "uk_recording_sessions_member_active" -> RecordingErrorCode.ACTIVE_RECORDING_ALREADY_EXISTS;
                    case "uk_recording_sessions_member_request" -> RecordingErrorCode.RECORDING_START_REQUEST_CONFLICT;
                    default -> RecordingErrorCode.INVALID_RECORDING_DATA;
                });
            }
            cause = cause.getCause();
        }
        return new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
    }
}
