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

class NotionImportRunTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @DisplayName("실패 상태는 저장값 없이 고정된 공개 사유를 반환한다")
    @Test
    void publicFailureReason_success_failedStatus() {
        // given
        NotionImportRun importRun = createImportRun(NotionImportStatus.FAILED);

        // when
        String publicFailureReason = importRun.publicFailureReason();

        // then
        assertThat(publicFailureReason).isEqualTo("Notion 문서를 가져오지 못했습니다");
    }

    @DisplayName("실패 상태가 아니면 공개 실패 사유를 반환하지 않는다")
    @Test
    void publicFailureReason_success_nonFailedStatus() {
        // given
        NotionImportRun importRun = createImportRun(NotionImportStatus.RUNNING);

        // when
        String publicFailureReason = importRun.publicFailureReason();

        // then
        assertThat(publicFailureReason).isNull();
    }

    @DisplayName("처리한 Page 수가 전체 Page 수보다 크면 생성할 수 없다")
    @Test
    void create_failure_processedPageCountExceedsTotal() {
        // given
        ThrowingCallable action = () -> NotionImportRun.create(
                1L,
                2L,
                3L,
                NotionImportStatus.RUNNING,
                10,
                11,
                CREATED_AT.plusSeconds(1),
                null,
                CREATED_AT
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class)
                .extracting(exception -> ((NotionImportException) exception).getErrorCode())
                .isEqualTo(NotionImportErrorCode.INVALID_NOTION_IMPORT_RUN);
    }

    @DisplayName("상태에 맞는 시작·완료 시각 조합으로 생성한다")
    @MethodSource("validStatusTimestampCases")
    @ParameterizedTest(name = "{0}")
    void create_success_validStatusTimestampCombination(
            String caseName,
            NotionImportStatus status,
            Instant startedAt,
            Instant completedAt
    ) {
        // given

        // when
        NotionImportRun importRun = createImportRun(
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
            NotionImportStatus status,
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
        assertThat(thrown).isInstanceOf(NotionImportException.class)
                .extracting(exception -> ((NotionImportException) exception).getErrorCode())
                .isEqualTo(NotionImportErrorCode.INVALID_NOTION_IMPORT_RUN);
    }

    @DisplayName("대기 중인 Import Run을 시작하면 실행 중 상태와 시작·heartbeat 시각을 기록한다")
    @Test
    void start_success_pendingRun() {
        // given
        NotionImportRun importRun = createPendingImportRun();
        Instant startedAt = CREATED_AT.plusSeconds(1);

        // when
        importRun.start(startedAt);

        // then
        assertThat(importRun.getStatus()).isEqualTo(NotionImportStatus.RUNNING);
        assertThat(importRun.getStartedAt()).isEqualTo(startedAt);
        assertThat(importRun.getLastHeartbeatAt()).isEqualTo(startedAt);
        assertThat(importRun.getCompletedAt()).isNull();
    }

    @DisplayName("실행 중인 Import Run은 다시 시작할 수 없다")
    @Test
    void start_failure_runningRun() {
        // given
        NotionImportRun importRun = createImportRun(NotionImportStatus.RUNNING);
        ThrowingCallable action = () -> importRun.start(CREATED_AT.plusSeconds(2));

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class);
    }

    @DisplayName("모든 Page를 처리한 실행 중 Import Run을 완료한다")
    @Test
    void complete_success_allPagesProcessed() {
        // given
        NotionImportRun importRun = createRunningImportRun();
        importRun.preparePageCount(2);
        importRun.recordProcessedPage();
        importRun.recordProcessedPage();
        Instant completedAt = CREATED_AT.plusSeconds(2);

        // when
        importRun.complete(completedAt);

        // then
        assertThat(importRun.getStatus()).isEqualTo(NotionImportStatus.COMPLETED);
        assertThat(importRun.getTotalPageCount()).isEqualTo(2);
        assertThat(importRun.getProcessedPageCount()).isEqualTo(2);
        assertThat(importRun.getCompletedAt()).isEqualTo(completedAt);
        assertThat(importRun.getLastHeartbeatAt()).isNull();
    }

    @DisplayName("0건인 수집 결과는 전체 Page 수로 준비할 수 없다")
    @Test
    void preparePageCount_failure_emptyResult() {
        // given
        NotionImportRun importRun = createRunningImportRun();
        ThrowingCallable action = () -> importRun.preparePageCount(0);

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class);
        assertThat(importRun.getTotalPageCount()).isNull();
        assertThat(importRun.getProcessedPageCount()).isZero();
    }

    @DisplayName("전체 Page 수는 실행 중 한 번만 준비한다")
    @Test
    void preparePageCount_failure_alreadyPrepared() {
        // given
        NotionImportRun importRun = createRunningImportRun();
        importRun.preparePageCount(2);
        ThrowingCallable action = () -> importRun.preparePageCount(2);

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class);
        assertThat(importRun.getTotalPageCount()).isEqualTo(2);
    }

    @DisplayName("처리하지 않은 Page가 남은 Import Run은 완료할 수 없다")
    @Test
    void complete_failure_unprocessedPageRemains() {
        // given
        NotionImportRun importRun = createRunningImportRun();
        importRun.preparePageCount(2);
        importRun.recordProcessedPage();
        ThrowingCallable action = () -> importRun.complete(CREATED_AT.plusSeconds(2));

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class);
        assertThat(importRun.getStatus()).isEqualTo(NotionImportStatus.RUNNING);
    }

    @DisplayName("전체 Page 수를 넘겨 처리할 수 없다")
    @Test
    void recordProcessedPage_failure_totalPageCountExceeded() {
        // given
        NotionImportRun importRun = createRunningImportRun();
        importRun.preparePageCount(1);
        importRun.recordProcessedPage();
        ThrowingCallable action = importRun::recordProcessedPage;

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class);
        assertThat(importRun.getProcessedPageCount()).isEqualTo(1);
    }

    @DisplayName("대기 중인 stale Import Run을 실패 처리하면 회수 시각을 시작·완료 시각으로 기록한다")
    @Test
    void fail_success_pendingRun() {
        // given
        NotionImportRun importRun = createPendingImportRun();
        Instant failedAt = CREATED_AT.plusSeconds(10);

        // when
        importRun.fail(failedAt);

        // then
        assertThat(importRun.getStatus()).isEqualTo(NotionImportStatus.FAILED);
        assertThat(importRun.getStartedAt()).isEqualTo(failedAt);
        assertThat(importRun.getCompletedAt()).isEqualTo(failedAt);
        assertThat(importRun.getLastHeartbeatAt()).isNull();
    }

    @DisplayName("실행 중인 Import Run을 실패 처리하면 기존 시작 시각을 보존한다")
    @Test
    void fail_success_runningRun() {
        // given
        NotionImportRun importRun = createRunningImportRun();
        Instant failedAt = CREATED_AT.plusSeconds(10);

        // when
        importRun.fail(failedAt);

        // then
        assertThat(importRun.getStatus()).isEqualTo(NotionImportStatus.FAILED);
        assertThat(importRun.getLastHeartbeatAt()).isNull();
        assertThat(importRun.getStartedAt()).isEqualTo(CREATED_AT.plusSeconds(1));
        assertThat(importRun.getCompletedAt()).isEqualTo(failedAt);
    }

    @DisplayName("완료된 Import Run은 실패 상태로 되돌릴 수 없다")
    @Test
    void fail_failure_completedRun() {
        // given
        NotionImportRun importRun = createImportRun(NotionImportStatus.COMPLETED);
        ThrowingCallable action = () -> importRun.fail(CREATED_AT.plusSeconds(10));

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class);
        assertThat(importRun.getStatus()).isEqualTo(NotionImportStatus.COMPLETED);
    }

    @DisplayName("시작 시각보다 앞선 시각으로 Import Run을 완료할 수 없다")
    @Test
    void complete_failure_beforeStartedAt() {
        // given
        NotionImportRun importRun = createRunningImportRun();
        importRun.preparePageCount(1);
        importRun.recordProcessedPage();
        ThrowingCallable action = () -> importRun.complete(CREATED_AT);

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class);
        assertThat(importRun.getStatus()).isEqualTo(NotionImportStatus.RUNNING);
    }

    private NotionImportRun createImportRun(NotionImportStatus status) {
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

    private NotionImportRun createPendingImportRun() {
        return NotionImportRun.create(
                1L,
                2L,
                3L,
                NotionImportStatus.PENDING,
                null,
                0,
                null,
                null,
                CREATED_AT
        );
    }

    private NotionImportRun createRunningImportRun() {
        NotionImportRun importRun = createPendingImportRun();
        importRun.start(CREATED_AT.plusSeconds(1));
        return importRun;
    }

    private NotionImportRun createImportRun(
            NotionImportStatus status,
            Instant startedAt,
            Instant completedAt
    ) {
        return NotionImportRun.create(
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
                        NotionImportStatus.PENDING,
                        null,
                        null
                ),
                Arguments.of(
                        "running",
                        NotionImportStatus.RUNNING,
                        CREATED_AT.plusSeconds(1),
                        null
                ),
                Arguments.of(
                        "completed",
                        NotionImportStatus.COMPLETED,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "failed",
                        NotionImportStatus.FAILED,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                )
        );
    }

    private static Stream<Arguments> invalidStatusTimestampCases() {
        return Stream.of(
                Arguments.of(
                        "pending-with-started-at",
                        NotionImportStatus.PENDING,
                        CREATED_AT.plusSeconds(1),
                        null
                ),
                Arguments.of(
                        "pending-with-completed-at",
                        NotionImportStatus.PENDING,
                        null,
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "running-without-started-at",
                        NotionImportStatus.RUNNING,
                        null,
                        null
                ),
                Arguments.of(
                        "running-with-completed-at",
                        NotionImportStatus.RUNNING,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "completed-without-started-at",
                        NotionImportStatus.COMPLETED,
                        null,
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "completed-without-completed-at",
                        NotionImportStatus.COMPLETED,
                        CREATED_AT.plusSeconds(1),
                        null
                ),
                Arguments.of(
                        "failed-without-started-at",
                        NotionImportStatus.FAILED,
                        null,
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        "failed-without-completed-at",
                        NotionImportStatus.FAILED,
                        CREATED_AT.plusSeconds(1),
                        null
                ),
                Arguments.of(
                        "completed-before-started-at",
                        NotionImportStatus.COMPLETED,
                        CREATED_AT.plusSeconds(2),
                        CREATED_AT.plusSeconds(1)
                ),
                Arguments.of(
                        "failed-before-started-at",
                        NotionImportStatus.FAILED,
                        CREATED_AT.plusSeconds(2),
                        CREATED_AT.plusSeconds(1)
                )
        );
    }
}
