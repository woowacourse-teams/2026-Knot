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
import org.junit.jupiter.params.provider.EnumSource;
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

    @DisplayName("FAILED Run은 재시도할 수 있다")
    @Test
    void validateRetryable_success_failedStatus() {
        // given
        ContentImportRun importRun = createImportRun(ContentImportStatus.FAILED);

        // when
        Throwable thrown = catchThrowable(importRun::validateRetryable);

        // then
        assertThat(thrown).isNull();
    }

    @DisplayName("FAILED가 아닌 Run은 재시도할 수 없다")
    @EnumSource(value = ContentImportStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    @ParameterizedTest(name = "{0}")
    void validateRetryable_failure_nonFailedStatus(ContentImportStatus status) {
        // given
        ContentImportRun importRun = createImportRun(status);
        ThrowingCallable action = importRun::validateRetryable;

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class)
                .extracting(exception -> ((ContentImportException) exception).contentImportErrorCode())
                .isEqualTo(ContentImportErrorCode.CONTENT_IMPORT_NOT_RETRYABLE);
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

    @DisplayName("대기 중인 Import Run을 시작하면 실행 중 상태와 시작·heartbeat 시각을 기록한다")
    @Test
    void start_success_pendingRun() {
        // given
        ContentImportRun importRun = createPendingImportRun();
        Instant startedAt = CREATED_AT.plusSeconds(1);

        // when
        importRun.start(startedAt);

        // then
        assertThat(importRun.getStatus()).isEqualTo(ContentImportStatus.RUNNING);
        assertThat(importRun.getStartedAt()).isEqualTo(startedAt);
        assertThat(importRun.getLastHeartbeatAt()).isEqualTo(startedAt);
        assertThat(importRun.getCompletedAt()).isNull();
    }

    @DisplayName("실행 중인 Import Run은 다시 시작할 수 없다")
    @Test
    void start_failure_runningRun() {
        // given
        ContentImportRun importRun = createImportRun(ContentImportStatus.RUNNING);
        ThrowingCallable action = () -> importRun.start(CREATED_AT.plusSeconds(2));

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class);
    }

    @DisplayName("모든 Page를 처리한 실행 중 Import Run을 완료한다")
    @Test
    void complete_success_allPagesProcessed() {
        // given
        ContentImportRun importRun = createRunningImportRun();
        importRun.preparePageCount(2);
        importRun.recordProcessedPage();
        importRun.recordProcessedPage();
        Instant completedAt = CREATED_AT.plusSeconds(2);

        // when
        importRun.complete(completedAt);

        // then
        assertThat(importRun.getStatus()).isEqualTo(ContentImportStatus.COMPLETED);
        assertThat(importRun.getTotalPageCount()).isEqualTo(2);
        assertThat(importRun.getProcessedPageCount()).isEqualTo(2);
        assertThat(importRun.getCompletedAt()).isEqualTo(completedAt);
        assertThat(importRun.getLastHeartbeatAt()).isNull();
    }

    @DisplayName("0건인 수집 결과는 전체 Page 수로 준비할 수 없다")
    @Test
    void preparePageCount_failure_emptyResult() {
        // given
        ContentImportRun importRun = createRunningImportRun();
        ThrowingCallable action = () -> importRun.preparePageCount(0);

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class);
        assertThat(importRun.getTotalPageCount()).isNull();
        assertThat(importRun.getProcessedPageCount()).isZero();
    }

    @DisplayName("전체 Page 수는 실행 중 한 번만 준비한다")
    @Test
    void preparePageCount_failure_alreadyPrepared() {
        // given
        ContentImportRun importRun = createRunningImportRun();
        importRun.preparePageCount(2);
        ThrowingCallable action = () -> importRun.preparePageCount(2);

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class);
        assertThat(importRun.getTotalPageCount()).isEqualTo(2);
    }

    @DisplayName("처리하지 않은 Page가 남은 Import Run은 완료할 수 없다")
    @Test
    void complete_failure_unprocessedPageRemains() {
        // given
        ContentImportRun importRun = createRunningImportRun();
        importRun.preparePageCount(2);
        importRun.recordProcessedPage();
        ThrowingCallable action = () -> importRun.complete(CREATED_AT.plusSeconds(2));

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class);
        assertThat(importRun.getStatus()).isEqualTo(ContentImportStatus.RUNNING);
    }

    @DisplayName("전체 Page 수를 넘겨 처리할 수 없다")
    @Test
    void recordProcessedPage_failure_totalPageCountExceeded() {
        // given
        ContentImportRun importRun = createRunningImportRun();
        importRun.preparePageCount(1);
        importRun.recordProcessedPage();
        ThrowingCallable action = importRun::recordProcessedPage;

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class);
        assertThat(importRun.getProcessedPageCount()).isEqualTo(1);
    }

    @DisplayName("대기 중인 stale Import Run을 실패 처리하면 회수 시각을 시작·완료 시각으로 기록한다")
    @Test
    void fail_success_pendingRun() {
        // given
        ContentImportRun importRun = createPendingImportRun();
        Instant failedAt = CREATED_AT.plusSeconds(10);

        // when
        importRun.fail(failedAt);

        // then
        assertThat(importRun.getStatus()).isEqualTo(ContentImportStatus.FAILED);
        assertThat(importRun.getStartedAt()).isEqualTo(failedAt);
        assertThat(importRun.getCompletedAt()).isEqualTo(failedAt);
        assertThat(importRun.getLastHeartbeatAt()).isNull();
    }

    @DisplayName("실행 중인 Import Run을 실패 처리하면 기존 시작 시각을 보존한다")
    @Test
    void fail_success_runningRun() {
        // given
        ContentImportRun importRun = createRunningImportRun();
        Instant failedAt = CREATED_AT.plusSeconds(10);

        // when
        importRun.fail(failedAt);

        // then
        assertThat(importRun.getStatus()).isEqualTo(ContentImportStatus.FAILED);
        assertThat(importRun.getLastHeartbeatAt()).isNull();
        assertThat(importRun.getStartedAt()).isEqualTo(CREATED_AT.plusSeconds(1));
        assertThat(importRun.getCompletedAt()).isEqualTo(failedAt);
    }

    @DisplayName("완료된 Import Run은 실패 상태로 되돌릴 수 없다")
    @Test
    void fail_failure_completedRun() {
        // given
        ContentImportRun importRun = createImportRun(ContentImportStatus.COMPLETED);
        ThrowingCallable action = () -> importRun.fail(CREATED_AT.plusSeconds(10));

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class);
        assertThat(importRun.getStatus()).isEqualTo(ContentImportStatus.COMPLETED);
    }

    @DisplayName("시작 시각보다 앞선 시각으로 Import Run을 완료할 수 없다")
    @Test
    void complete_failure_beforeStartedAt() {
        // given
        ContentImportRun importRun = createRunningImportRun();
        importRun.preparePageCount(1);
        importRun.recordProcessedPage();
        ThrowingCallable action = () -> importRun.complete(CREATED_AT);

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class);
        assertThat(importRun.getStatus()).isEqualTo(ContentImportStatus.RUNNING);
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

    private ContentImportRun createPendingImportRun() {
        return ContentImportRun.create(
                1L,
                2L,
                3L,
                ContentImportStatus.PENDING,
                null,
                0,
                null,
                null,
                CREATED_AT
        );
    }

    private ContentImportRun createRunningImportRun() {
        ContentImportRun importRun = createPendingImportRun();
        importRun.start(CREATED_AT.plusSeconds(1));
        return importRun;
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
