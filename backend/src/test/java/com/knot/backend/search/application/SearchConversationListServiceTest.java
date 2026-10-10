package com.knot.backend.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.search.application.dto.query.SearchConversationListParameters;
import com.knot.backend.search.application.dto.result.SearchConversationListItemResult;
import com.knot.backend.search.application.dto.result.SearchConversationListResult;
import com.knot.backend.search.domain.SearchConversationCursor;
import com.knot.backend.search.domain.SearchException;
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
class SearchConversationListServiceTest {
    private static final Instant TIME = Instant.parse("2026-10-10T00:00:00.123456Z");

    @Mock
    private WorkspaceMemberRepository memberships;
    @Mock
    private SearchConversationListQuery query;
    private SearchConversationListService service;

    @BeforeEach
    void setUp() {
        service = new SearchConversationListService(
                memberships,
                query
        );
    }

    @Test
    @DisplayName("빈 목록은 빈 배열과 null 커서를 반환한다")
    void findEmpty_success() {
        // given
        allowMembership();
        when(
                query.findPage(
                        1,
                        2,
                        20,
                        null
                )
        ).thenReturn(List.of());

        // when
        SearchConversationListResult result = service.find(
                1,
                2,
                SearchConversationListParameters.of(
                        null,
                        null
                )
        );

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("초과 행을 제외하고 마지막 반환 행으로 커서를 만든다")
    void findNextPage_success() {
        // given
        allowMembership();
        when(
                query.findPage(
                        1,
                        2,
                        2,
                        null
                )
        ).thenReturn(
                List.of(
                        item(12),
                        item(11),
                        item(10)
                )
        );

        // when
        SearchConversationListResult result = service.find(
                1,
                2,
                SearchConversationListParameters.of(
                        null,
                        2
                )
        );

        // then
        assertThat(result.items()).extracting(SearchConversationListItemResult::id)
                .containsExactly(
                        12L,
                        11L
                );
        SearchConversationCursor cursor = SearchConversationCursor.parse(
                result.nextCursor(),
                1,
                2
        );
        assertThat(cursor.getConversationId()).isEqualTo(11);
        assertThat(cursor.getUpdatedAt()).isEqualTo(TIME);
    }

    @Test
    @DisplayName("결과가 정확히 size개이면 다음 커서는 없다")
    void findExactPage_success() {
        // given
        allowMembership();
        when(
                query.findPage(
                        1,
                        2,
                        2,
                        null
                )
        ).thenReturn(
                List.of(
                        item(12),
                        item(11)
                )
        );

        // when
        SearchConversationListResult result = service.find(
                1,
                2,
                SearchConversationListParameters.of(
                        null,
                        2
                )
        );

        // then
        assertThat(result.items()).hasSize(2);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("다음 요청은 파싱한 위치를 조회에 전달하고 크기는 바꿀 수 있다")
    void findCursor_success() {
        // given
        allowMembership();
        String encoded = SearchConversationCursor.of(
                1,
                2,
                TIME,
                11
        )
                .encode();
        when(
                query.findPage(
                        eq(1L),
                        eq(2L),
                        eq(100),
                        any(SearchConversationCursor.class)
                )
        ).thenReturn(List.of(item(10)));

        // when
        SearchConversationListResult result = service.find(
                1,
                2,
                SearchConversationListParameters.of(
                        encoded,
                        100
                )
        );

        // then
        assertThat(result.items()).extracting(SearchConversationListItemResult::id)
                .containsExactly(10L);
    }

    @Test
    @DisplayName("현재 멤버십이 없으면 데이터 조회 전에 거절한다")
    void findNonMember_failure() {
        // when & then
        assertThatExceptionOfType(WorkspaceException.class).isThrownBy(
                () -> service.find(
                        1,
                        2,
                        SearchConversationListParameters.of(
                                null,
                                null
                        )
                )
        );
        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("다른 멤버의 커서는 데이터 조회 전에 거절한다")
    void findOtherMemberCursor_failure() {
        // given
        allowMembership();
        String encoded = SearchConversationCursor.of(
                1,
                3,
                TIME,
                11
        )
                .encode();

        // when & then
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> service.find(
                        1,
                        2,
                        SearchConversationListParameters.of(
                                encoded,
                                20
                        )
                )
        );
        verifyNoInteractions(query);
    }

    private void allowMembership() {
        when(
                memberships.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
    }

    private SearchConversationListItemResult item(long id) {
        return new SearchConversationListItemResult(
                id,
                "질문",
                "답변",
                TIME,
                TIME
        );
    }
}
