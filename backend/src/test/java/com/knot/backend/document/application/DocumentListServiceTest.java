package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.application.dto.query.DocumentListParameters;
import com.knot.backend.document.application.dto.result.DocumentCardResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.application.dto.result.DocumentListResult;
import com.knot.backend.document.application.dto.result.DocumentTopicResult;
import com.knot.backend.document.domain.DocumentCursor;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentStatus;
import com.knot.backend.document.domain.MyConfirmationState;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentListServiceTest {
    @Mock
    private WorkspaceMemberRepository members;
    @Mock
    private DocumentListQuery query;
    private DocumentListService service;

    @BeforeEach
    void setUp() {
        service = new DocumentListService(
                members,
                query
        );
    }

    @Test
    @DisplayName("초과 행은 제외하고 마지막 반환 카드로 다음 커서를 만든다")
    void find_success_nextCursor() {
        DocumentListParameters parameters = DocumentListParameters.of(
                null,
                1,
                null,
                null
        );
        when(
                members.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                query.findTopics(
                        1,
                        2,
                        parameters
                )
        ).thenReturn(
                List.of(
                        new DocumentTopicResult(
                                "정책",
                                2
                        )
                )
        );
        when(
                query.findPage(
                        1,
                        2,
                        parameters,
                        null
                )
        ).thenReturn(
                List.of(
                        card(3),
                        card(2)
                )
        );

        DocumentListResult result = service.find(
                1,
                2,
                parameters
        );

        assertThat(result.items()).extracting(DocumentCardResult::id)
                .containsExactly(3L);
        assertThat(
                result.topics()
                        .getFirst()
                        .documentCount()
        ).isEqualTo(2);
        assertThat(
                DocumentCursor.parse(
                        result.nextCursor(),
                        1,
                        2,
                        null,
                        null
                )
                        .getDocumentId()
        ).isEqualTo(3);
    }

    @Test
    @DisplayName("정확히 size개이거나 빈 페이지면 다음 커서가 없다")
    void find_success_lastPage() {
        DocumentListParameters parameters = DocumentListParameters.of(
                null,
                1,
                null,
                null
        );
        when(
                members.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                query.findTopics(
                        1,
                        2,
                        parameters
                )
        ).thenReturn(List.of());
        when(
                query.findPage(
                        1,
                        2,
                        parameters,
                        null
                )
        ).thenReturn(List.of(card(3)))
                .thenReturn(List.of());

        assertThat(
                service.find(
                        1,
                        2,
                        parameters
                )
                        .nextCursor()
        ).isNull();
        assertThat(
                service.find(
                        1,
                        2,
                        parameters
                )
                        .items()
        ).isEmpty();
    }

    @Test
    @DisplayName("이어 조회할 때 페이지 크기를 바꿀 수 있다")
    void find_success_changedSize() {
        String cursor = DocumentCursor.of(
                1,
                2,
                null,
                null,
                card(3).createdAt(),
                3
        )
                .encode();
        DocumentListParameters parameters = DocumentListParameters.of(
                cursor,
                100,
                null,
                null
        );
        when(
                members.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                query.findTopics(
                        1,
                        2,
                        parameters
                )
        ).thenReturn(List.of());
        when(
                query.findPage(
                        eq(1L),
                        eq(2L),
                        eq(parameters),
                        any(DocumentCursor.class)
                )
        ).thenReturn(List.of(card(2)));

        assertThat(
                service.find(
                        1,
                        2,
                        parameters
                )
                        .items()
        ).extracting(DocumentCardResult::id)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("비멤버는 커서나 목록을 조회하지 않고 접근을 거절한다")
    void find_failure_accessDenied() {
        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        DocumentListParameters.of(
                                null,
                                null,
                                null,
                                null
                        )
                )
        ).isInstanceOf(WorkspaceException.class);
        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("다른 조건의 커서는 DB 조회 전에 거절한다")
    void find_failure_cursorScope() {
        when(
                members.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        String cursor = DocumentCursor.of(
                1,
                2,
                null,
                null,
                card(3).createdAt(),
                3
        )
                .encode();

        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        DocumentListParameters.of(
                                cursor,
                                50,
                                MyConfirmationState.PENDING,
                                null
                        )
                )
        ).isInstanceOf(DocumentException.class);
        verifyNoInteractions(query);
    }

    private DocumentCardResult card(long id) {
        return new DocumentCardResult(
                id,
                42,
                "정책",
                "제목",
                null,
                DocumentStatus.DRAFT,
                Instant.parse("2026-10-06T00:00:00.123456Z"),
                10,
                MyConfirmationState.NOT_REQUIRED,
                new DocumentConfirmationSummaryResult(
                        0,
                        0,
                        0
                )
        );
    }
}
