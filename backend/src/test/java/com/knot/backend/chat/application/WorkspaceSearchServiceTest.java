package com.knot.backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.chat.application.dto.result.WorkspaceSearchResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.search.application.PublishedDocumentSearchService;
import com.knot.backend.search.application.SearchContext;
import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.search.domain.SearchResultStatus;
import com.knot.backend.workspace.application.WorkspaceQueryService;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceSearchServiceTest {
    private static final long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final SearchChunk CHUNK = SearchChunk.retrieved(
            WORKSPACE_ID,
            201L,
            301L,
            3,
            "DB 기술 선정 회의록",
            "https://notion.test/db",
            Instant.parse("2026-09-01T00:00:00Z"),
            "PostgreSQL은 pgvector 확장을 위해 선택했다.",
            0.95
    );

    private final WorkspaceQueryService workspaceQueryService = mock(WorkspaceQueryService.class);
    private final PublishedDocumentSearchService documentSearchService = mock(PublishedDocumentSearchService.class);
    private final WorkspaceSearchService service = new WorkspaceSearchService(
            workspaceQueryService,
            documentSearchService
    );

    @Test
    @DisplayName("근거를 찾으면 규칙 문장과 예산에 맞춘 청크를 돌려주고 아무것도 저장하지 않는다")
    void search_success_ready() {
        // given
        when(
                documentSearchService.search(
                        WORKSPACE_ID,
                        "질문"
                )
        ).thenReturn(
                SearchContext.ready(
                        List.of(CHUNK),
                        12000
                )
        );

        // when
        WorkspaceSearchResult result = service.search(
                WORKSPACE_ID,
                MEMBER_ID,
                "질문"
        );

        // then
        assertThat(result.isReady()).isTrue();
        assertThat(result.groundingRules()).isEqualTo(SearchContext.groundingRules());
        assertThat(result.chunks()).extracting(SearchChunk::chunkIndex)
                .containsExactly(3);
        assertThat(result.fallbackAnswer()).isNull();
        verify(workspaceQueryService).findDetail(
                WORKSPACE_ID,
                MEMBER_ID
        );
        verify(documentSearchService).requirePublishedSnapshot(WORKSPACE_ID);
    }

    @Test
    @DisplayName("관련 문서가 없으면 NO_RESULT와 안내 문구를 돌려준다")
    void search_success_noResult() {
        // given
        when(
                documentSearchService.search(
                        WORKSPACE_ID,
                        "질문"
                )
        ).thenReturn(SearchContext.noResult());

        // when
        WorkspaceSearchResult result = service.search(
                WORKSPACE_ID,
                MEMBER_ID,
                "질문"
        );

        // then
        assertThat(result.status()).isEqualTo(SearchResultStatus.NO_RESULT);
        assertThat(result.fallbackAnswer()).contains("찾지 못했습니다");
        assertThat(result.groundingRules()).isNull();
        assertThat(result.chunks()).isEmpty();
    }

    @Test
    @DisplayName("범위가 넓은 질문은 NEEDS_CLARIFICATION과 구체화 안내를 돌려준다")
    void search_success_needsClarification() {
        // given
        when(
                documentSearchService.search(
                        WORKSPACE_ID,
                        "우리 프로젝트 어때?"
                )
        ).thenReturn(SearchContext.needsClarification());

        // when
        WorkspaceSearchResult result = service.search(
                WORKSPACE_ID,
                MEMBER_ID,
                "우리 프로젝트 어때?"
        );

        // then
        assertThat(result.status()).isEqualTo(SearchResultStatus.NEEDS_CLARIFICATION);
        assertThat(result.fallbackAnswer()).contains("범위가 넓어요");
    }

    @Test
    @DisplayName("공개된 문서가 없으면 CHAT_DOCUMENTS_NOT_READY로 바꾸고 검색하지 않는다")
    void search_failure_documentsNotReady() {
        // given
        doThrow(new SearchException(SearchErrorCode.SEARCH_IMPORT_NOT_READY)).when(documentSearchService)
                .requirePublishedSnapshot(WORKSPACE_ID);

        // when
        ThrowingCallable action = () -> service.search(
                WORKSPACE_ID,
                MEMBER_ID,
                "질문"
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.CHAT_DOCUMENTS_NOT_READY)
        );
        verify(
                documentSearchService,
                never()
        ).search(
                anyLong(),
                anyString()
        );
    }

    @Test
    @DisplayName("임베딩 provider 오류는 검색 코드를 그대로 전달한다(로드맵 Q31)")
    void search_failure_providerFailedPassesThrough() {
        // given
        when(
                documentSearchService.search(
                        WORKSPACE_ID,
                        "질문"
                )
        ).thenThrow(new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED));

        // when
        ThrowingCallable action = () -> service.search(
                WORKSPACE_ID,
                MEMBER_ID,
                "질문"
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 Workspace 예외를 그대로 올리고 스냅샷·검색을 부르지 않는다")
    void search_failure_accessDenied() {
        // given
        when(
                workspaceQueryService.findDetail(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenThrow(new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));

        // when
        ThrowingCallable action = () -> service.search(
                WORKSPACE_ID,
                MEMBER_ID,
                "질문"
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                WorkspaceException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED)
        );
        verify(
                documentSearchService,
                never()
        ).requirePublishedSnapshot(anyLong());
        verify(
                documentSearchService,
                never()
        ).search(
                anyLong(),
                anyString()
        );
    }
}
