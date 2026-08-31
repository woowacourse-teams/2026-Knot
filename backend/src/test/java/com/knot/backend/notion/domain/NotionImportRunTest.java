package com.knot.backend.notion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        assertThat(thrown).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.INVALID_NOTION_IMPORT_RUN);
    }

    private NotionImportRun createImportRun(NotionImportStatus status) {
        return NotionImportRun.create(
                1L,
                2L,
                3L,
                status,
                10,
                4,
                CREATED_AT.plusSeconds(1),
                status == NotionImportStatus.RUNNING ? null : CREATED_AT.plusSeconds(2),
                CREATED_AT
        );
    }
}
