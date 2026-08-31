package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.NotionImportStatusResult;
import com.knot.backend.workspace.domain.NotionErrorCode;
import com.knot.backend.workspace.domain.NotionException;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import com.knot.backend.workspace.domain.NotionImportStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NotionImportQueryServiceTest {
    private static final Long IMPORT_RUN_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    private final NotionImportRunRepository importRunRepository = mock(NotionImportRunRepository.class);
    private final NotionImportQueryService service = new NotionImportQueryService(importRunRepository);

    @DisplayName("조회 가능한 Import Run의 네 상태를 응답 계약으로 변환한다")
    @MethodSource("statusCases")
    @ParameterizedTest(name = "{0}")
    void findStatus_success_statusContract(
            NotionImportStatus status,
            Integer totalPageCount,
            int processedPageCount,
            String failureReason,
            Instant startedAt,
            Instant completedAt
    ) {
        // given
        NotionImportRun importRun = mockImportRun(
                status,
                totalPageCount,
                processedPageCount,
                failureReason,
                startedAt,
                completedAt
        );
        when(
                importRunRepository.findVisibleByIdAndMemberId(
                        IMPORT_RUN_ID,
                        MEMBER_ID
                )
        ).thenReturn(Optional.of(importRun));

        // when
        NotionImportStatusResult result = service.findStatus(
                IMPORT_RUN_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).isEqualTo(
                new NotionImportStatusResult(
                        IMPORT_RUN_ID,
                        status,
                        totalPageCount,
                        processedPageCount,
                        failureReason,
                        CREATED_AT,
                        startedAt,
                        completedAt
                )
        );
        verify(importRunRepository).findVisibleByIdAndMemberId(
                IMPORT_RUN_ID,
                MEMBER_ID
        );
    }

    @DisplayName("Import Run ID가 양수가 아니면 저장소를 조회하지 않는다")
    @Test
    void findStatus_failure_invalidImportRunId() {
        // given
        ThrowingCallable action = () -> service.findStatus(
                0L,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.INVALID_NOTION_IMPORT_RUN_ID);
        verifyNoInteractions(importRunRepository);
    }

    @DisplayName("Import Run이 없거나 현재 멤버에게 보이지 않으면 같은 오류를 반환한다")
    @Test
    void findStatus_failure_notVisible() {
        // given
        when(
                importRunRepository.findVisibleByIdAndMemberId(
                        IMPORT_RUN_ID,
                        MEMBER_ID
                )
        ).thenReturn(Optional.empty());
        ThrowingCallable action = () -> service.findStatus(
                IMPORT_RUN_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.NOTION_IMPORT_RUN_NOT_FOUND);
    }

    private NotionImportRun mockImportRun(
            NotionImportStatus status,
            Integer totalPageCount,
            int processedPageCount,
            String failureReason,
            Instant startedAt,
            Instant completedAt
    ) {
        NotionImportRun importRun = mock(NotionImportRun.class);
        when(importRun.getId()).thenReturn(IMPORT_RUN_ID);
        when(importRun.getStatus()).thenReturn(status);
        when(importRun.getTotalPageCount()).thenReturn(totalPageCount);
        when(importRun.getProcessedPageCount()).thenReturn(processedPageCount);
        when(importRun.publicFailureReason()).thenReturn(failureReason);
        when(importRun.getCreatedAt()).thenReturn(CREATED_AT);
        when(importRun.getStartedAt()).thenReturn(startedAt);
        when(importRun.getCompletedAt()).thenReturn(completedAt);
        return importRun;
    }

    private static Stream<Arguments> statusCases() {
        return Stream.of(
                Arguments.of(
                        NotionImportStatus.PENDING,
                        null,
                        0,
                        null,
                        null,
                        null
                ),
                Arguments.of(
                        NotionImportStatus.RUNNING,
                        10,
                        4,
                        null,
                        CREATED_AT.plusSeconds(1),
                        null
                ),
                Arguments.of(
                        NotionImportStatus.COMPLETED,
                        10,
                        10,
                        null,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                ),
                Arguments.of(
                        NotionImportStatus.FAILED,
                        10,
                        4,
                        "Notion 문서를 가져오지 못했습니다",
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                )
        );
    }
}
