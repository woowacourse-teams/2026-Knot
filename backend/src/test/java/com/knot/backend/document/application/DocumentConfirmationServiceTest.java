package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.application.dto.query.DocumentConfirmationParameters;
import com.knot.backend.document.application.dto.result.DocumentConfirmationItemResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationOverviewResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationsResult;
import com.knot.backend.document.domain.DocumentConfirmationCursor;
import com.knot.backend.document.domain.DocumentConfirmationState;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentConfirmationServiceTest {
    private static final DocumentConfirmationParameters PARAMETERS = DocumentConfirmationParameters.of(
            null,
            2
    );
    @Mock
    private WorkspaceMemberRepository workspaceMembers;
    @Mock
    private DocumentConfirmationQuery query;
    private DocumentConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentConfirmationService(
                workspaceMembers,
                query
        );
    }

    @Test
    @DisplayName("비멤버는 문서나 대상 존재 여부를 조회하지 않는다")
    void find_failure_accessDenied() {
        // when & then
        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        3,
                        PARAMETERS
                )
        ).isInstanceOf(WorkspaceException.class)
                .extracting(
                        error -> ((WorkspaceException) error).getErrorCode()
                                .getCode()
                )
                .isEqualTo("WORKSPACE_ACCESS_DENIED");
        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("조회 범위에 문서가 없으면 대상 페이지를 읽지 않고 404다")
    void find_failure_missingDocument() {
        // given
        authorize();
        when(
                query.findSummary(
                        1,
                        3,
                        2
                )
        ).thenReturn(Optional.empty());
        // when & then
        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        3,
                        PARAMETERS
                )
        ).isInstanceOf(DocumentException.class)
                .extracting(
                        error -> ((DocumentException) error).getErrorCode()
                                .getCode()
                )
                .isEqualTo("DOCUMENT_NOT_FOUND");
        verify(query).findSummary(
                1,
                3,
                2
        );
        verifyNoMoreInteractions(query);
    }

    @Test
    @DisplayName("잘못된 커서는 DB 상세 조회 전에 거절한다")
    void find_failure_invalidCursor() {
        // given
        authorize();
        // when & then
        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        3,
                        DocumentConfirmationParameters.of(
                                "invalid",
                                2
                        )
                )
        ).isInstanceOf(DocumentException.class);
        verifyNoInteractions(query);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("빈 페이지·마지막 페이지·추가 대상 없는 정확한 크기에는 다음 커서가 없다")
    void find_success_lastPage(int count) {
        // given
        authorize();
        overview();
        List<DocumentConfirmationItemResult> page = IntStream.rangeClosed(
                1,
                count
        )
                .mapToObj(
                        id -> item(
                                id,
                                DocumentConfirmationState.PENDING
                        )
                )
                .toList();
        when(
                query.findPage(
                        1,
                        3,
                        2,
                        null
                )
        ).thenReturn(page);
        // when
        DocumentConfirmationsResult result = service.find(
                1,
                2,
                3,
                PARAMETERS
        );
        // then
        assertThat(result.documentId()).isEqualTo(3);
        assertThat(result.summary()).isEqualTo(
                new DocumentConfirmationSummaryResult(
                        3,
                        2,
                        2
                )
        );
        assertThat(result.confirmedByMe()).isTrue();
        assertThat(result.items()).containsExactlyElementsOf(page);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("추가 대상이 있으면 마지막 반환 대상의 상태와 ID로 커서를 만든다")
    void find_success_nextPageBoundary() {
        // given
        authorize();
        overview();
        when(
                query.findPage(
                        1,
                        3,
                        2,
                        null
                )
        ).thenReturn(
                List.of(
                        item(
                                10,
                                DocumentConfirmationState.CONFIRMED
                        ),
                        item(
                                20,
                                DocumentConfirmationState.PENDING
                        ),
                        item(
                                30,
                                DocumentConfirmationState.EXCLUDED
                        )
                )
        );
        // when
        DocumentConfirmationsResult result = service.find(
                1,
                2,
                3,
                PARAMETERS
        );
        // then
        assertThat(result.items()).extracting(DocumentConfirmationItemResult::memberId)
                .containsExactly(
                        10L,
                        20L
                );
        DocumentConfirmationCursor cursor = DocumentConfirmationCursor.parse(
                result.nextCursor(),
                1,
                3,
                2
        );
        assertThat(cursor.getState()).isEqualTo(DocumentConfirmationState.PENDING);
        assertThat(cursor.getTargetMemberId()).isEqualTo(20);
    }

    @Test
    @DisplayName("전달된 커서의 경계를 복원해 페이지 쿼리에 전달한다")
    void find_success_cursorForwarded() {
        // given
        authorize();
        overview();
        String encoded = DocumentConfirmationCursor.of(
                1,
                3,
                2,
                DocumentConfirmationState.CONFIRMED,
                10
        )
                .encode();
        when(
                query.findPage(
                        eq(1L),
                        eq(3L),
                        eq(1),
                        any(DocumentConfirmationCursor.class)
                )
        ).thenReturn(List.of());
        // when
        service.find(
                1,
                2,
                3,
                DocumentConfirmationParameters.of(
                        encoded,
                        1
                )
        );
        // then
        ArgumentCaptor<DocumentConfirmationCursor> cursor = ArgumentCaptor.forClass(DocumentConfirmationCursor.class);
        verify(query).findPage(
                eq(1L),
                eq(3L),
                eq(1),
                cursor.capture()
        );
        assertThat(
                cursor.getValue()
                        .getTargetMemberId()
        ).isEqualTo(10);
        assertThat(
                cursor.getValue()
                        .getState()
        ).isEqualTo(DocumentConfirmationState.CONFIRMED);
    }

    private void authorize() {
        when(
                workspaceMembers.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
    }

    private void overview() {
        when(
                query.findSummary(
                        1,
                        3,
                        2
                )
        ).thenReturn(
                Optional.of(
                        new DocumentConfirmationOverviewResult(
                                3,
                                new DocumentConfirmationSummaryResult(
                                        3,
                                        2,
                                        2
                                ),
                                true
                        )
                )
        );
    }

    private DocumentConfirmationItemResult item(
            long memberId,
            DocumentConfirmationState state
    ) {
        if (state == DocumentConfirmationState.CONFIRMED) {
            return new DocumentConfirmationItemResult(
                    memberId,
                    "대상",
                    null,
                    Instant.parse("2026-10-06T00:00:00Z"),
                    state
            );
        }
        return new DocumentConfirmationItemResult(
                memberId,
                "대상",
                null,
                null,
                state
        );
    }
}
