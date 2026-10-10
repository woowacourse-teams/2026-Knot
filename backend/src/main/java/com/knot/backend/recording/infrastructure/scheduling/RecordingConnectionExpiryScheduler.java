package com.knot.backend.recording.infrastructure.scheduling;

import com.knot.backend.recording.application.RecordingConnectionExpiryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class RecordingConnectionExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecordingConnectionExpiryScheduler.class);

    private final RecordingConnectionExpiryService expiryService;
    private final RecordingConnectionExpiryProperties properties;

    @Scheduled(fixedDelayString = "${recording.connection-expiry.interval}")
    public void expireDisconnectedRecordings() {
        int expiredCount = expiryService.expireDisconnected(properties.batchSize());
        if (expiredCount > 0) {
            log.info(
                    "연결이 끊긴 녹음 {}건을 종료했습니다",
                    expiredCount
            );
        }
    }
}
