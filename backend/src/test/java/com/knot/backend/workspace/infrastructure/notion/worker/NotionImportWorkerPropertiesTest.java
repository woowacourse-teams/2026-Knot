package com.knot.backend.workspace.infrastructure.notion.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NotionImportWorkerPropertiesTest {

    @DisplayName("worker 주기와 stale 기준은 양수이고 복구 batch 크기는 1 이상이어야 한다")
    @MethodSource("invalidProperties")
    @ParameterizedTest(name = "{0}")
    void create_failure_invalidProperties(
            String caseName,
            Duration pollDelay,
            Duration pendingStaleTimeout,
            Duration runningStaleTimeout,
            int recoveryBatchSize
    ) {
        // given
        ThrowingCallable action = () -> new NotionImportWorkerProperties(
                pollDelay,
                pendingStaleTimeout,
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
                        "zero-pending-timeout",
                        positive,
                        Duration.ZERO,
                        positive,
                        1
                ),
                Arguments.of(
                        "negative-running-timeout",
                        positive,
                        positive,
                        Duration.ofSeconds(-1),
                        1
                ),
                Arguments.of(
                        "zero-batch-size",
                        positive,
                        positive,
                        positive,
                        0
                )
        );
    }
}
