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

    @DisplayName("수동 Import 요청은 Page 수와 실행 시각이 비어 있는 PENDING Run을 생성한다")
    @Test
    void createPending_success() {
        // given
        long workspaceId = 1L;
        long connectionId = 2L;
        long requestedByMemberId = 3L;

        // when
        ContentImportRun importRun = ContentImportRun.createPending(
                workspaceId,
                connectionId,
                requestedByMemberId,
                CREATED_AT
        );

        // then
        assertThat(importRun.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(importRun.getContentSourceConnectionId()).isEqualTo(connectionId);
        assertThat(importRun.getRequestedByMemberId()).isEqualTo(requestedByMemberId);
        assertThat(importRun.getStatus()).isEqualTo(ContentImportStatus.PENDING);
        assertThat(importRun.getTotalPageCount()).isNull();
        assertThat(importRun.getProcessedPageCount()).isZero();
        assertThat(importRun.getStartedAt()).isNull();
        assertThat(importRun.getCompletedAt()).isNull();
        assertThat(importRun.getCreatedAt()).isEqualTo(CREATED_AT);
    }

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

    @DisplayName("대기 상태는 처리한 Page 수가 0이어야 한다")
    @Test
    void create_failure_pendingWithProcessedPageCount() {
        // given
        ThrowingCallable action = () -> ContentImportRun.create(
                1L,
                2L,
                3L,
                ContentImportStatus.PENDING,
                10,
                1,
                null,
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

    @DisplayName("완료 상태는 전체 Page 수가 확정되고 처리한 Page 수와 같아야 한다")
    @MethodSource("invalidCompletedPageCountCases")
    @ParameterizedTest(name = "{0}")
    void create_failure_invalidCompletedPageCounts(
            String caseName,
            Integer totalPageCount,
            int processedPageCount
    ) {
        // given
        ThrowingCallable action = () -> ContentImportRun.create(
                1L,
                2L,
                3L,
                ContentImportStatus.COMPLETED,
                totalPageCount,
                processedPageCount,
                CREATED_AT.plusSeconds(1),
                CREATED_AT.plusSeconds(2),
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
        int processedPageCount = switch (status) {
            case PENDING -> 0;
            case RUNNING, FAILED -> 4;
            case COMPLETED -> 10;
        };
        return ContentImportRun.create(
                1L,
                2L,
                3L,
                status,
                10,
                processedPageCount,
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
                        "running-at-created-at",
                        ContentImportStatus.RUNNING,
                        CREATED_AT,
                        null
                ),
                Arguments.of(
                        "completed",
                        ContentImportStatus.COMPLETED,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "completed-at-created-at",
                        ContentImportStatus.COMPLETED,
                        CREATED_AT,
                        CREATED_AT
                ),
                Arguments.of(
                        "failed",
                        ContentImportStatus.FAILED,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "failed-at-created-at",
                        ContentImportStatus.FAILED,
                        CREATED_AT,
                        CREATED_AT
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
                ),
                Arguments.of(
                        "running-before-created-at",
                        ContentImportStatus.RUNNING,
                        CREATED_AT.minusSeconds(1),
                        null
                ),
                Arguments.of(
                        "completed-started-before-created-at",
                        ContentImportStatus.COMPLETED,
                        CREATED_AT.minusSeconds(1),
                        CREATED_AT.plusSeconds(1)
                ),
                Arguments.of(
                        "failed-started-before-created-at",
                        ContentImportStatus.FAILED,
                        CREATED_AT.minusSeconds(1),
                        CREATED_AT.plusSeconds(1)
                )
        );
    }

    private static Stream<Arguments> invalidCompletedPageCountCases() {
        return Stream.of(
                Arguments.of(
                        "completed-without-total-page-count",
                        null,
                        0
                ),
                Arguments.of(
                        "completed-with-zero-pages",
                        0,
                        0
                ),
                Arguments.of(
                        "completed-with-unprocessed-page",
                        10,
                        9
                )
        );
    }
}
