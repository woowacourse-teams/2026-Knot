package com.knot.backend.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.application.ChatSearchService;
import com.knot.backend.chat.application.dto.result.ChatSearchResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.presentation.dto.request.SearchChatMessageRequest;
import com.knot.backend.chat.presentation.dto.response.ChatSearchResponse;
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

class ChatSearchControllerTest {

    @Test
    @DisplayName("READY 검색 결과를 규칙 문장·청크와 함께 200으로 반환하고 캐시를 막는다")
    void search_success_ready() {
        // given
        ChatSearchService service = Mockito.mock(ChatSearchService.class);
        ChatSearchController controller = new ChatSearchController(service);
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
                        10L,
                        2L,
                        "질문"
                )
        )
                .thenReturn(
                        ChatSearchResult.ready(
                                101L,
                                "규칙",
                                List.of(chunk)
                        )
                );

        // when
        ResponseEntity<ChatSearchResponse> response = controller.search(
                10L,
                new SearchChatMessageRequest("질문"),
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
        ChatSearchResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(SearchResultStatus.READY);
        assertThat(body.userMessageId()).isEqualTo(101L);
        assertThat(body.groundingRules()).isEqualTo("규칙");
        assertThat(body.assistantMessageId()).isNull();
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
    @DisplayName("READY가 아닌 결과는 청크 없이 저장된 안내 답변만 반환한다")
    void search_success_fallback() {
        // given
        ChatSearchService service = Mockito.mock(ChatSearchService.class);
        ChatSearchController controller = new ChatSearchController(service);
        Mockito.when(
                service.search(
                        10L,
                        2L,
                        "질문"
                )
        )
                .thenReturn(
                        ChatSearchResult.fallback(
                                SearchResultStatus.NO_RESULT,
                                101L,
                                102L,
                                "찾지 못했습니다"
                        )
                );

        // when
        ResponseEntity<ChatSearchResponse> response = controller.search(
                10L,
                new SearchChatMessageRequest("질문"),
                AuthenticatedMember.of(
                        2L,
                        "흑곰",
                        null
                )
        );

        // then
        ChatSearchResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(SearchResultStatus.NO_RESULT);
        assertThat(body.userMessageId()).isEqualTo(101L);
        assertThat(body.assistantMessageId()).isEqualTo(102L);
        assertThat(body.fallbackAnswer()).isEqualTo("찾지 못했습니다");
        assertThat(body.groundingRules()).isNull();
        assertThat(body.chunks()).isNull();
    }

    @Test
    @DisplayName("진행 중인 턴이 있으면 서비스 예외를 그대로 전달한다")
    void search_failure_turnInProgress() {
        // given
        ChatSearchService service = Mockito.mock(ChatSearchService.class);
        ChatSearchController controller = new ChatSearchController(service);
        Mockito.when(
                service.search(
                        10L,
                        2L,
                        "질문"
                )
        )
                .thenThrow(new ChatException(ChatErrorCode.CHAT_TURN_IN_PROGRESS));

        // when
        ThrowingCallable action = () -> controller.search(
                10L,
                new SearchChatMessageRequest("질문"),
                AuthenticatedMember.of(
                        2L,
                        "흑곰",
                        null
                )
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                ChatException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.CHAT_TURN_IN_PROGRESS)
        );
    }
}
