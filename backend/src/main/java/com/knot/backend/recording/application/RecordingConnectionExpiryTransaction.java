package com.knot.backend.recording.application;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordingConnectionExpiryTransaction {
    private final RecordingSessionRepository recordingSessionRepository;
    private final Clock clock;

    // 다른 요청과 같은 녹음 잠금 아래에서 최신 신호를 다시 판정한다. Workspace·Member는 잠그지 않는다.
    @Transactional
    public boolean expire(long recordingId) {
        Optional<RecordingSession> found = recordingSessionRepository.findByIdForUpdate(recordingId);
        if (found.isEmpty()) {
            return false;
        }
        RecordingSession session = found.orElseThrow();
        if (!session.expireIfDisconnected(
                clock.instant()
                        .truncatedTo(ChronoUnit.MICROS)
        )) {
            return false;
        }
        recordingSessionRepository.save(session);
        return true;
    }
}
