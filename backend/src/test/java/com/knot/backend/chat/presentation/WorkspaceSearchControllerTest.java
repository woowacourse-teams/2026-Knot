package com.knot.backend.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.WorkspaceSearchService;
import com.knot.backend.chat.application.dto.result.WorkspaceSearchResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.presentation.dto.request.WorkspaceSearchRequest;
import com.knot.backend.chat.presentation.dto.response.WorkspaceSearchResponse;
import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchResultStatus;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class WorkspaceSearchControllerTest {

    @Test
    @DisplayName("READY 검색 결과를 규칙 문장·청크와 함께 200으로 반환하고 캐시를 막는다")
    void search_success_ready() {
        // given
        WorkspaceSearchService service = Mockito.mock(WorkspaceSearchService.class);
        WorkspaceSearchController controller = new WorkspaceSearchController(service);
        SearchChunk chunk = SearchChunk.retrieved(
                1L,
                201L,
                301L,
                2,
                "DB 기술 선정 회의록",
                "https://notion.test/db",
                Instant.parse("2026-09-01T00:00:00Z"),
                "PostgreSQL은 pgvector 확장을 위해 선택했다.",
                0.95
        );
        Mockito.when(
                service.search(
                        1L,
                        2L,
                        "질문"
                )
        )
                .thenReturn(
                        WorkspaceSearchResult.ready(
                                "규칙",
                                List.of(chunk)
                        )
                );

        // when
        ResponseEntity<WorkspaceSearchResponse> response = controller.search(
                1L,
                new WorkspaceSearchRequest("질문"),
                AuthenticatedMember.of(
                        2L,
                        "흑곰",
                        null
                )
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                response.getHeaders()
                        .getFirst(HttpHeaders.CACHE_CONTROL)
        ).isEqualTo("no-store");
        WorkspaceSearchResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(SearchResultStatus.READY);
        assertThat(body.groundingRules()).isEqualTo("규칙");
        assertThat(body.fallbackAnswer()).isNull();
        assertThat(body.chunks()).singleElement()
                .satisfies(item -> {
                    assertThat(item.importRunId()).isEqualTo(301L);
                    assertThat(item.importedPageId()).isEqualTo(201L);
                    assertThat(item.chunkIndex()).isEqualTo(2);
                    assertThat(item.title()).isEqualTo("DB 기술 선정 회의록");
                    assertThat(item.sourceUrl()).isEqualTo("https://notion.test/db");
                    assertThat(item.content()).contains("pgvector");
                    assertThat(item.score()).isEqualTo(0.95);
                });
    }

    @Test
    @DisplayName("READY가 아닌 결과는 청크 없이 안내 문구만 반환한다")
    void search_success_fallback() {
        // given
        WorkspaceSearchService service = Mockito.mock(WorkspaceSearchService.class);
        WorkspaceSearchController controller = new WorkspaceSearchController(service);
        Mockito.when(
                service.search(
                        1L,
                        2L,
                        "질문"
                )
        )
                .thenReturn(
                        WorkspaceSearchResult.fallback(
                                SearchResultStatus.NO_RESULT,
                                "찾지 못했습니다"
                        )
                );

        // when
        ResponseEntity<WorkspaceSearchResponse> response = controller.search(
                1L,
                new WorkspaceSearchRequest("질문"),
                AuthenticatedMember.of(
                        2L,
                        "흑곰",
                        null
                )
        );

        // then
        WorkspaceSearchResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(SearchResultStatus.NO_RESULT);
        assertThat(body.fallbackAnswer()).isEqualTo("찾지 못했습니다");
        assertThat(body.groundingRules()).isNull();
        assertThat(body.chunks()).isNull();
    }

    @Test
    @DisplayName("문서가 준비되지 않았으면 서비스 예외를 그대로 전달한다")
    void search_failure_documentsNotReady() {
        // given
        WorkspaceSearchService service = Mockito.mock(WorkspaceSearchService.class);
        WorkspaceSearchController controller = new WorkspaceSearchController(service);
        Mockito.when(
                service.search(
                        1L,
                        2L,
                        "질문"
                )
        )
                .thenThrow(new ChatException(ChatErrorCode.CHAT_DOCUMENTS_NOT_READY));

        // when
        ThrowingCallable action = () -> controller.search(
                1L,
                new WorkspaceSearchRequest("질문"),
                AuthenticatedMember.of(
                        2L,
                        "흑곰",
                        null
                )
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.CHAT_DOCUMENTS_NOT_READY)
        );
    }
}
