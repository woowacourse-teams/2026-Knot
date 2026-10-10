package com.knot.backend.recording.application;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordingConnectionExpiryService {
    private static final Logger log = LoggerFactory.getLogger(RecordingConnectionExpiryService.class);

    private final RecordingSessionRepository recordingSessionRepository;
    private final RecordingConnectionExpiryTransaction expiryTransaction;
    private final Clock clock;

    // 요청이 오지 않아 남은 활성 녹음을 찾아 녹음마다 따로 커밋한다
    public int expireDisconnected(int batchSize) {
        Instant threshold = RecordingSession.disconnectionThreshold(
                clock.instant()
                        .truncatedTo(ChronoUnit.MICROS)
        );
        List<Long> candidateIds = recordingSessionRepository.findActiveIdsLastSeenAtOrBefore(
                threshold,
                batchSize
        );
        int expiredCount = 0;
        for (long recordingId : candidateIds) {
            if (expireIsolated(recordingId)) {
                expiredCount++;
            }
        }
        return expiredCount;
    }

    // 한 녹음의 실패로 다른 녹음의 회수를 멈추지 않는다. 실패한 녹음은 활성으로 남아 다음 실행에서 다시 판정한다.
    private boolean expireIsolated(long recordingId) {
        try {
            return expiryTransaction.expire(recordingId);
        } catch (RuntimeException exception) {
            log.warn(
                    "녹음 연결 만료 회수에 실패했습니다. recordingId={}",
                    recordingId,
                    exception
            );
            return false;
        }
    }
}
