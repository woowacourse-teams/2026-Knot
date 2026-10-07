package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.result.RecordingCurrentResult;
import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordingCurrentService {
    private final RecordingWorkspaceAccessValidator workspaceAccessValidator;
    private final RecordingSessionRepository recordingSessionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<RecordingCurrentResult> findCurrent(
            long workspaceId,
            long memberId
    ) {
        workspaceAccessValidator.validate(
                workspaceId,
                memberId
        );
        Optional<RecordingSession> activeSession = recordingSessionRepository.findActiveByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        );
        // 조회 전에 시각을 잡으면 그 사이 커밋된 재개·시작의 구간 시작 시각보다 앞설 수 있다.
        Instant now = clock.instant();
        return activeSession.map(
                session -> RecordingCurrentResult.of(
                        session,
                        now
                )
        );
    }
}
