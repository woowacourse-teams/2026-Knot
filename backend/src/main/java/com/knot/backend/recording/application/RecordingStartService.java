package com.knot.backend.recording.application;

import com.knot.backend.member.domain.MemberRepository;
import com.knot.backend.recording.application.dto.command.RecordingStartCommand;
import com.knot.backend.recording.application.dto.result.RecordingStartResult;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordingStartService {
    private final RecordingWorkspaceAccessValidator workspaceAccessValidator;
    private final MemberRepository memberRepository;
    private final RecordingSessionRepository recordingSessionRepository;
    private final RecordingControlTokenHasher controlTokenHasher;
    private final Clock clock;

    @Transactional
    public RecordingStartResult start(
            long workspaceId,
            long memberId,
            RecordingStartCommand command
    ) {
        workspaceAccessValidator.validateAndLock(
                workspaceId,
                memberId
        );
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new RecordingException(RecordingErrorCode.RECORDING_MEMBER_NOT_FOUND));

        String controlTokenHash = controlTokenHasher.hash(command.controlToken());
        Optional<RecordingSession> existing = recordingSessionRepository.findByMemberIdAndRequestIdForUpdate(
                memberId,
                command.requestId()
        );
        if (existing.isPresent()) {
            return replay(
                    existing.orElseThrow(),
                    workspaceId,
                    command,
                    controlTokenHash
            );
        }
        reclaimDisconnectedOrRejectActive(memberId);

        RecordingSession session = RecordingSession.start(
                workspaceId,
                memberId,
                command.requestId(),
                command.tabId(),
                controlTokenHash,
                now()
        );
        return RecordingStartResult.from(
                recordingSessionRepository.save(session),
                true
        );
    }

    private RecordingStartResult replay(
            RecordingSession session,
            long workspaceId,
            RecordingStartCommand command,
            String controlTokenHash
    ) {
        if (!session.matchesStart(
                workspaceId,
                command.tabId(),
                controlTokenHash
        )) {
            throw new RecordingException(RecordingErrorCode.RECORDING_START_REQUEST_CONFLICT);
        }
        if (session.expireIfDisconnected(now())) {
            recordingSessionRepository.save(session);
        }
        return RecordingStartResult.from(
                session,
                false
        );
    }

    private void reclaimDisconnectedOrRejectActive(long memberId) {
        Instant now = now();
        for (RecordingSession active : recordingSessionRepository.findAllActiveByMemberIdForUpdate(memberId)) {
            if (!active.expireIfDisconnected(now)) {
                throw new RecordingException(RecordingErrorCode.ACTIVE_RECORDING_ALREADY_EXISTS);
            }
            recordingSessionRepository.save(active);
        }
    }

    private Instant now() {
        return clock.instant()
                .truncatedTo(ChronoUnit.MICROS);
    }
}
