package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.knot.backend.document.application.dto.query.DocumentGenerationJobListParameters;
import com.knot.backend.document.application.dto.result.DocumentGenerationJobItemResult;
import com.knot.backend.document.application.dto.result.DocumentGenerationJobListResult;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationJobCursor;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentGenerationJobListServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");

    private WorkspaceMemberRepository memberships;
    private DocumentGenerationJobListQuery query;
    private DocumentGenerationJobListService service;

    @BeforeEach
    void setUp() {
        memberships = mock(WorkspaceMemberRepository.class);
        query = mock(DocumentGenerationJobListQuery.class);
        service = new DocumentGenerationJobListService(
                memberships,
                query,
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    @DisplayName("추가 한 항목으로 다음 페이지를 판단하고 마지막 반환 Job으로 커서를 만든다")
    void find_success_nextPage() {
        // given
        when(
                memberships.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                query.findPage(
                        1,
                        3,
                        null,
                        NOW
                )
        ).thenReturn(
                List.of(
                        item(3),
                        item(2),
                        item(1)
                )
        );

        // when
        DocumentGenerationJobListResult result = service.find(
                1,
                2,
                DocumentGenerationJobListParameters.of(
                        null,
                        2
                )
        );

        // then
        assertThat(result.items()).extracting(DocumentGenerationJobItemResult::jobId)
                .containsExactly(
                        3L,
                        2L
                );
        assertThat(
                DocumentGenerationJobCursor.parse(
                        result.nextCursor(),
                        1,
                        2
                )
                        .getJobId()
        ).isEqualTo(2);
        verify(query).findPage(
                1,
                3,
                null,
                NOW
        );
    }

    @Test
    @DisplayName("정확히 size개이거나 비어 있으면 다음 커서는 없다")
    void find_success_lastPage() {
        // given
        when(
                memberships.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                query.findPage(
                        1,
                        2,
                        null,
                        NOW
                )
        ).thenReturn(List.of(item(1)));

        // when
        DocumentGenerationJobListResult result = service.find(
                1,
                2,
                DocumentGenerationJobListParameters.of(
                        null,
                        1
                )
        );

        // then
        assertThat(result.items()).hasSize(1);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("기본 크기는 20이며 빈 결과는 빈 배열과 null 커서다")
    void find_success_emptyDefaultPage() {
        // given
        when(
                memberships.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                query.findPage(
                        1,
                        21,
                        null,
                        NOW
                )
        ).thenReturn(List.of());

        // when
        DocumentGenerationJobListResult result = service.find(
                1,
                2,
                DocumentGenerationJobListParameters.of(
                        null,
                        null
                )
        );

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("비멤버는 저장소를 조회하지 않는다")
    void find_failure_nonMember() {
        // when & then
        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        DocumentGenerationJobListParameters.of(
                                null,
                                20
                        )
                )
        ).isInstanceOf(WorkspaceException.class);
        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("다른 조회 범위의 커서는 저장소 조회 전에 거절한다")
    void find_failure_cursorScope() {
        // given
        when(
                memberships.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        String cursor = DocumentGenerationJobCursor.of(
                9,
                2,
                NOW,
                1
        )
                .encode();

        // when & then
        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        DocumentGenerationJobListParameters.of(
                                cursor,
                                20
                        )
                )
        ).isInstanceOf(DocumentException.class);
        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("정상 커서는 동일한 경계와 요청당 고정 시각으로 저장소에 전달한다")
    void find_success_cursor() {
        // given
        when(
                memberships.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                query.findPage(
                        eq(1L),
                        eq(21),
                        any(DocumentGenerationJobCursor.class),
                        eq(NOW)
                )
        ).thenReturn(List.of());
        String cursor = DocumentGenerationJobCursor.of(
                1,
                2,
                NOW,
                3
        )
                .encode();

        // when
        DocumentGenerationJobListResult result = service.find(
                1,
                2,
                DocumentGenerationJobListParameters.of(
                        cursor,
                        20
                )
        );

        // then
        assertThat(result.items()).isEmpty();
        verify(query).findPage(
                eq(1L),
                eq(21),
                any(DocumentGenerationJobCursor.class),
                eq(NOW)
        );
    }

    private DocumentGenerationJobItemResult item(long id) {
        return new DocumentGenerationJobItemResult(
                id,
                42,
                null,
                DocumentGenerationJobStatus.QUEUED,
                NOW,
                NOW
        );
    }
}
