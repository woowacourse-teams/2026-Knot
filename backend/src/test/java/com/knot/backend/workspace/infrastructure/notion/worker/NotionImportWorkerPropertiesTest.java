package com.knot.backend.workspace.infrastructure.notion.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NotionImportWorkerPropertiesTest {

    @DisplayName("millisecond 단위 heartbeat가 stale 기준보다 짧으면 설정을 생성한다")
    @Test
    void create_success_validMillisecondProperties() {
        // given

        // when
        NotionImportWorkerProperties properties = new NotionImportWorkerProperties(
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                Duration.ofMillis(2),
                1
        );

        // then
        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofMillis(1));
    }

    @DisplayName("poll·heartbeat·stale 주기는 유효하고 heartbeat는 stale 기준보다 짧아야 한다")
    @MethodSource("invalidProperties")
    @ParameterizedTest(name = "{0}")
    void create_failure_invalidProperties(
            String caseName,
            Duration pollDelay,
            Duration heartbeatInterval,
            Duration runningStaleTimeout,
            int recoveryBatchSize
    ) {
        // given
        ThrowingCallable action = () -> new NotionImportWorkerProperties(
                pollDelay,
                heartbeatInterval,
                runningStaleTimeout,
                recoveryBatchSize
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notion Import worker 설정이 올바르지 않습니다");
    }

    private static Stream<Arguments> invalidProperties() {
        Duration positive = Duration.ofSeconds(1);
        return Stream.of(
                Arguments.of(
                        "missing-poll-delay",
                        null,
                        positive,
                        positive,
                        1
                ),
                Arguments.of(
                        "zero-heartbeat-interval",
                        positive,
                        Duration.ZERO,
                        positive,
                        1
                ),
                Arguments.of(
                        "sub-millisecond-heartbeat",
                        positive,
                        Duration.ofNanos(1),
                        positive,
                        1
                ),
                Arguments.of(
                        "same-effective-millisecond",
                        positive,
                        Duration.ofNanos(1_500_000),
                        Duration.ofNanos(1_900_000),
                        1
                ),
                Arguments.of(
                        "heartbeat-millisecond-overflow",
                        positive,
                        Duration.ofSeconds(Long.MAX_VALUE - 1),
                        Duration.ofSeconds(Long.MAX_VALUE),
                        1
                ),
                Arguments.of(
                        "running-timeout-millisecond-overflow",
                        positive,
                        positive,
                        Duration.ofSeconds(Long.MAX_VALUE),
                        1
                ),
                Arguments.of(
                        "heartbeat-not-shorter-than-running-timeout",
                        positive,
                        positive,
                        positive,
                        1
                ),
                Arguments.of(
                        "negative-running-timeout",
                        positive,
                        Duration.ofMillis(500),
                        Duration.ofSeconds(-1),
                        1
                ),
                Arguments.of(
                        "zero-batch-size",
                        positive,
                        Duration.ofMillis(500),
                        Duration.ofSeconds(1),
                        0
                )
        );
    }
}
