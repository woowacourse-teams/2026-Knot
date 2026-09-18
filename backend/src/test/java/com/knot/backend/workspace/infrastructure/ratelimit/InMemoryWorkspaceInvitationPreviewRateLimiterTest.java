package com.knot.backend.workspace.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.global.exception.ProjectException;
import com.knot.backend.global.exception.RetryAfterException;
import com.knot.backend.workspace.application.WorkspaceInvitationPreviewRateLimiter;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryWorkspaceInvitationPreviewRateLimiterTest {
    private static final String REMOTE_ADDRESS = "203.0.113.10";

    @DisplayName("같은 IP의 코드 조회는 고정 1분 창에서 30회까지 허용한다")
    @Test
    void consume_success_allowsThirtyAttempts() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        WorkspaceInvitationPreviewRateLimiter rateLimiter = rateLimiter(clock);

        // when
        ThrowingCallable action = () -> consumeTimes(
                rateLimiter,
                REMOTE_ADDRESS,
                30
        );

        // then
        assertThatCode(action).doesNotThrowAnyException();
    }

    @DisplayName("같은 IP의 코드 조회 31번째 요청은 429 예외로 차단한다")
    @Test
    void consume_failure_blocksThirtyFirstAttempt() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        WorkspaceInvitationPreviewRateLimiter rateLimiter = rateLimiter(clock);
        consumeTimes(
                rateLimiter,
                REMOTE_ADDRESS,
                30
        );

        // when
        ThrowingCallable action = () -> rateLimiter.consume(REMOTE_ADDRESS);

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                ProjectException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_RATE_LIMIT_EXCEEDED);
                    assertThat(((RetryAfterException) exception).getRetryAfterSeconds()).isEqualTo(60);
                }
        );
    }

    @DisplayName("첫 시도 기준 1분 창이 지나면 같은 IP의 코드 조회를 다시 허용한다")
    @Test
    void consume_success_resetsFixedWindow() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        WorkspaceInvitationPreviewRateLimiter rateLimiter = rateLimiter(clock);
        consumeTimes(
                rateLimiter,
                REMOTE_ADDRESS,
                30
        );
        clock.advance(Duration.ofMinutes(1));

        // when
        rateLimiter.consume(REMOTE_ADDRESS);

        // then
        assertThatCode(
                () -> consumeTimes(
                        rateLimiter,
                        REMOTE_ADDRESS,
                        29
                )
        ).doesNotThrowAnyException();
    }

    @DisplayName("서로 다른 IP의 코드 조회 횟수는 독립적으로 계산한다")
    @Test
    void consume_success_separatesRemoteAddress() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        WorkspaceInvitationPreviewRateLimiter rateLimiter = rateLimiter(clock);
        consumeTimes(
                rateLimiter,
                REMOTE_ADDRESS,
                30
        );

        // when
        ThrowingCallable action = () -> rateLimiter.consume("203.0.113.11");

        // then
        assertThatCode(action).doesNotThrowAnyException();
    }

    @DisplayName("창 만료 1초 전 차단되면 Retry-After는 최소 1초다")
    @Test
    void consume_failure_retryAfterMinimumOneSecond() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        WorkspaceInvitationPreviewRateLimiter rateLimiter = rateLimiter(clock);
        consumeTimes(
                rateLimiter,
                REMOTE_ADDRESS,
                30
        );
        clock.advance(Duration.ofSeconds(59))
                .advance(Duration.ofMillis(999));

        // when
        ThrowingCallable action = () -> rateLimiter.consume(REMOTE_ADDRESS);

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                ProjectException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_RATE_LIMIT_EXCEEDED);
                    assertThat(((RetryAfterException) exception).getRetryAfterSeconds()).isEqualTo(1);
                }
        );
    }

    @DisplayName("남은 제한 시간이 소수 초이면 Retry-After를 다음 정수 초로 올림한다")
    @Test
    void consume_failure_roundsRetryAfterUp() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        WorkspaceInvitationPreviewRateLimiter rateLimiter = rateLimiter(clock);
        consumeTimes(
                rateLimiter,
                REMOTE_ADDRESS,
                30
        );
        clock.advance(Duration.ofMillis(500));

        // when
        ThrowingCallable action = () -> rateLimiter.consume(REMOTE_ADDRESS);

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                RetryAfterException.class,
                exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(60)
        );
    }

    @DisplayName("추적 주소 한도 이후 새 IP는 bounded overflow 카운터를 공유한다")
    @Test
    void consume_failure_sharesOverflowCounterAtCapacity() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        WorkspaceInvitationPreviewRateLimiter rateLimiter = new InMemoryWorkspaceInvitationPreviewRateLimiter(
                clock,
                3
        );
        rateLimiter.consume("203.0.113.1");
        rateLimiter.consume("203.0.113.2");
        consumeTimes(
                rateLimiter,
                "203.0.113.3",
                30
        );

        // when
        ThrowingCallable action = () -> rateLimiter.consume("203.0.113.4");

        // then
        assertThatThrownBy(action).isInstanceOf(ProjectException.class)
                .extracting(exception -> ((ProjectException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_RATE_LIMIT_EXCEEDED);
    }

    @DisplayName("정리 시각이 지나면 만료된 주소 카운터를 제거하고 새 IP를 독립 추적한다")
    @Test
    void consume_success_cleansExpiredCounters() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        WorkspaceInvitationPreviewRateLimiter rateLimiter = new InMemoryWorkspaceInvitationPreviewRateLimiter(
                clock,
                3
        );
        rateLimiter.consume("203.0.113.1");
        rateLimiter.consume("203.0.113.2");
        clock.advance(Duration.ofMinutes(1));

        // when
        ThrowingCallable action = () -> {
            rateLimiter.consume("203.0.113.3");
            rateLimiter.consume("203.0.113.4");
        };

        // then
        assertThatCode(action).doesNotThrowAnyException();
    }

    private WorkspaceInvitationPreviewRateLimiter rateLimiter(Clock clock) {
        return new InMemoryWorkspaceInvitationPreviewRateLimiter(clock);
    }

    private void consumeTimes(
            WorkspaceInvitationPreviewRateLimiter rateLimiter,
            String remoteAddress,
            int count
    ) {
        for (int attempt = 0; attempt < count; attempt++) {
            rateLimiter.consume(remoteAddress);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private MutableClock advance(Duration duration) {
            instant = instant.plus(duration);
            return this;
        }
    }
}
