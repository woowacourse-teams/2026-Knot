package com.knot.backend.recording.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.recording.domain.RecordingSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingConnectionExpiryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:10:00.123456Z");
    private static final int BATCH_SIZE = 100;

    private RecordingSessionRepository recordingSessionRepository;
    private RecordingConnectionExpiryTransaction expiryTransaction;
    private RecordingConnectionExpiryService service;

    @BeforeEach
    void setUp() {
        recordingSessionRepository = mock(RecordingSessionRepository.class);
        expiryTransaction = mock(RecordingConnectionExpiryTransaction.class);
        service = new RecordingConnectionExpiryService(
                recordingSessionRepository,
                expiryTransaction,
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    @DisplayName("마지막 신호가 120초 이상 지난 활성 녹음을 정해진 개수만 찾아 녹음마다 회수하고 종료한 수를 돌려준다")
    void expireDisconnected_success_countsExpired() {
        // given
        when(
                recordingSessionRepository.findActiveIdsLastSeenAtOrBefore(
                        NOW.minusSeconds(120),
                        BATCH_SIZE
                )
        ).thenReturn(
                List.of(
                        1L,
                        2L
                )
        );
        when(expiryTransaction.expire(1L)).thenReturn(true);
        when(expiryTransaction.expire(2L)).thenReturn(false);

        // when
        int expiredCount = service.expireDisconnected(BATCH_SIZE);

        // then
        assertThat(expiredCount).isEqualTo(1);
        verify(expiryTransaction).expire(1L);
        verify(expiryTransaction).expire(2L);
    }

    @Test
    @DisplayName("한 녹음의 회수가 실패해도 다음 녹음을 계속 회수한다")
    void expireDisconnected_success_isolatesFailure() {
        // given
        when(
                recordingSessionRepository.findActiveIdsLastSeenAtOrBefore(
                        NOW.minusSeconds(120),
                        BATCH_SIZE
                )
        ).thenReturn(
                List.of(
                        1L,
                        2L
                )
        );
        when(expiryTransaction.expire(1L)).thenThrow(new IllegalStateException("잠금 시간 초과"));
        when(expiryTransaction.expire(2L)).thenReturn(true);

        // when
        int expiredCount = service.expireDisconnected(BATCH_SIZE);

        // then
        assertThat(expiredCount).isEqualTo(1);
        verify(expiryTransaction).expire(2L);
    }
}
