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
