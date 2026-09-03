package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.knot.backend.workspace.domain.ContentImportRunRepository;
import java.time.Duration;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ContentImportStaleRecoveryServiceTest {
    private final ContentImportRunRepository importRunRepository = mock(ContentImportRunRepository.class);
    private final ContentImportStaleRecoveryService staleRecoveryService = new ContentImportStaleRecoveryService(
            importRunRepository
    );

    @DisplayName("밀리초로 안전하게 표현할 수 없는 RUNNING timeout은 거부한다")
    @MethodSource("invalidRunningTimeouts")
    @ParameterizedTest(name = "{0}")
    void recover_failure_invalidRunningTimeout(
            String caseName,
            Duration runningTimeout
    ) {
        // given
        ThrowingCallable action = () -> staleRecoveryService.recover(
                runningTimeout,
                1
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Content Import stale 복구 설정이 올바르지 않습니다");
        verifyNoInteractions(importRunRepository);
    }

    private static Stream<Arguments> invalidRunningTimeouts() {
        return Stream.of(
                Arguments.of(
                        "1ms 미만",
                        Duration.ofNanos(1)
                ),
                Arguments.of(
                        "millis 변환 overflow",
                        Duration.ofSeconds(Long.MAX_VALUE)
                )
        );
    }
}
