package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ContentImportRunTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @DisplayName("처리한 Page 수가 전체 Page 수보다 크면 생성할 수 없다")
    @Test
    void create_failure_processedPageCountExceedsTotal() {
        // given
        ThrowingCallable action = () -> ContentImportRun.create(
                1L,
                2L,
                3L,
                ContentImportStatus.RUNNING,
                10,
                11,
                CREATED_AT.plusSeconds(1),
                null,
                CREATED_AT
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class)
                .extracting(exception -> ((ContentImportException) exception).getErrorCode())
                .isEqualTo(ContentImportErrorCode.INVALID_CONTENT_IMPORT_RUN);
    }

    @DisplayName("상태에 맞는 시작·완료 시각 조합으로 생성한다")
    @MethodSource("validStatusTimestampCases")
    @ParameterizedTest(name = "{0}")
    void create_success_validStatusTimestampCombination(
            String caseName,
            ContentImportStatus status,
            Instant startedAt,
            Instant completedAt
    ) {
        // given

        // when
        ContentImportRun importRun = createImportRun(
                status,
                startedAt,
                completedAt
        );

        // then
        assertThat(importRun.getStartedAt()).isEqualTo(startedAt);
        assertThat(importRun.getCompletedAt()).isEqualTo(completedAt);
    }

    @DisplayName("상태에 맞지 않는 시작·완료 시각 조합으로 생성할 수 없다")
    @MethodSource("invalidStatusTimestampCases")
    @ParameterizedTest(name = "{0}")
    void create_failure_invalidStatusTimestampCombination(
            String caseName,
            ContentImportStatus status,
            Instant startedAt,
            Instant completedAt
    ) {
        // given
        ThrowingCallable action = () -> createImportRun(
                status,
                startedAt,
                completedAt
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class)
                .extracting(exception -> ((ContentImportException) exception).getErrorCode())
                .isEqualTo(ContentImportErrorCode.INVALID_CONTENT_IMPORT_RUN);
    }

    private ContentImportRun createImportRun(ContentImportStatus status) {
        return switch (status) {
            case PENDING -> createImportRun(
                    status,
                    null,
                    null
            );
            case RUNNING -> createImportRun(
                    status,
                    CREATED_AT.plusSeconds(1),
                    null
            );
            case COMPLETED, FAILED -> createImportRun(
                    status,
                    CREATED_AT.plusSeconds(1),
                    CREATED_AT.plusSeconds(2)
            );
        };
    }

    private ContentImportRun createImportRun(
            ContentImportStatus status,
            Instant startedAt,
            Instant completedAt
    ) {
        return ContentImportRun.create(
                1L,
                2L,
                3L,
                status,
                10,
                4,
                startedAt,
                completedAt,
                CREATED_AT
        );
    }

    private static Stream<Arguments> validStatusTimestampCases() {
        return Stream.of(
                Arguments.of(
                        "pending",
                        ContentImportStatus.PENDING,
                        null,
                        null
                ),
                Arguments.of(
                        "running",
                        ContentImportStatus.RUNNING,
                        CREATED_AT.plusSeconds(1),
                        null
                ),
                Arguments.of(
                        "completed",
                        ContentImportStatus.COMPLETED,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "failed",
                        ContentImportStatus.FAILED,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                )
        );
    }

    private static Stream<Arguments> invalidStatusTimestampCases() {
        return Stream.of(
                Arguments.of(
                        "pending-with-started-at",
                        ContentImportStatus.PENDING,
                        CREATED_AT.plusSeconds(1),
                        null
                ),
                Arguments.of(
                        "pending-with-completed-at",
                        ContentImportStatus.PENDING,
                        null,
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "running-without-started-at",
                        ContentImportStatus.RUNNING,
                        null,
                        null
                ),
                Arguments.of(
                        "running-with-completed-at",
                        ContentImportStatus.RUNNING,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "completed-without-started-at",
                        ContentImportStatus.COMPLETED,
                        null,
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "completed-without-completed-at",
                        ContentImportStatus.COMPLETED,
                        CREATED_AT.plusSeconds(1),
                        null
                ),
                Arguments.of(
                        "failed-without-started-at",
                        ContentImportStatus.FAILED,
                        null,
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "failed-without-completed-at",
                        ContentImportStatus.FAILED,
                        CREATED_AT.plusSeconds(1),
                        null
                ),
                Arguments.of(
                        "completed-before-started-at",
                        ContentImportStatus.COMPLETED,
                        CREATED_AT.plusSeconds(2),
                        CREATED_AT.plusSeconds(1)
                ),
                Arguments.of(
                        "failed-before-started-at",
                        ContentImportStatus.FAILED,
                        CREATED_AT.plusSeconds(2),
                        CREATED_AT.plusSeconds(1)
                )
        );
    }
}
