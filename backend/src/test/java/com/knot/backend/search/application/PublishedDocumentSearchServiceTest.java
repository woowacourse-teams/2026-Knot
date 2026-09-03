package com.knot.backend.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchChunkRepository;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublishedDocumentSearchServiceTest {
    private static final SearchProperties PROPERTIES = new SearchProperties(
            1200,
            180,
            50,
            3,
            10000,
            64,
            0.35
    );
    private static final SearchChunk POSTGRES = chunk(
            7L,
            101L,
            11L,
            1,
            "DB 기술 선정 회의록",
            "PostgreSQL을 사용하기로 결정했다",
            0.9
    );

    @Test
    @DisplayName("published run 안에서 vector와 keyword 결과를 합쳐 page 기준 최대 3개로 선별한다")
    void search_success_hybridRanksDistinctSources() {
        // given
        SearchChunkRepository repository = mock(SearchChunkRepository.class);
        DocumentEmbeddingClient embeddingClient = mock(DocumentEmbeddingClient.class);
        when(repository.findPublishedImportRunId(7L)).thenReturn(Optional.of(11L));
        when(embeddingClient.embed(List.of("우리 DB 뭐 쓰기로 했지?"))).thenReturn(List.of(new double[1024]));
        SearchChunk second = chunk(
                7L,
                102L,
                11L,
                0,
                "백엔드 회의록",
                "PostgreSQL",
                0.7
        );
        SearchChunk third = chunk(
                7L,
                103L,
                11L,
                0,
                "프로젝트 기술 스택",
                "PostgreSQL",
                0.6
        );
        SearchChunk duplicatePage = chunk(
                7L,
                101L,
                11L,
                1,
                "DB 기술 선정 회의록",
                "다른 청크",
                0.95
        );
        when(
                repository.findByVector(
                        eq(7L),
                        eq(11L),
                        any(),
                        eq(50)
                )
        ).thenReturn(
                List.of(
                        POSTGRES,
                        duplicatePage,
                        second
                )
        );
        when(
                repository.findByKeywords(
                        eq(7L),
                        eq(11L),
                        any(),
                        eq(50)
                )
        ).thenReturn(
                List.of(
                        POSTGRES,
                        third
                )
        );
        PublishedDocumentSearchService service = new PublishedDocumentSearchService(
                repository,
                embeddingClient,
                new SearchQueryTerms(),
                new SearchQuestionClassifier(),
                PROPERTIES,
                new EmbeddingProperties(
                        "qwen-embedding",
                        1024
                )
        );

        // when
        SearchContext context = service.search(
                7L,
                "우리 DB 뭐 쓰기로 했지?"
        );

        // then
        assertThat(context.isReady()).isTrue();
        assertThat(context.references()).extracting(SearchChunk::title)
                .containsExactly(
                        "DB 기술 선정 회의록",
                        "백엔드 회의록",
                        "프로젝트 기술 스택"
                );
        assertThat(context.references()).extracting(SearchChunk::importRunId)
                .containsOnly(11L);
    }

    @Test
    @DisplayName("공개된 snapshot이 없으면 검색을 수행하지 않고 동기화 대기 오류를 반환한다")
    void search_failure_noPublishedSnapshot() {
        // given
        SearchChunkRepository repository = mock(SearchChunkRepository.class);
        DocumentEmbeddingClient embeddingClient = mock(DocumentEmbeddingClient.class);
        when(repository.findPublishedImportRunId(7L)).thenReturn(Optional.empty());
        PublishedDocumentSearchService service = new PublishedDocumentSearchService(
                repository,
                embeddingClient,
                new SearchQueryTerms(),
                new SearchQuestionClassifier(),
                PROPERTIES,
                new EmbeddingProperties(
                        "qwen-embedding",
                        1024
                )
        );

        // when
        ThrowingCallable action = () -> service.search(
                7L,
                "PostgreSQL"
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_IMPORT_NOT_READY)
        );
        verify(
                embeddingClient,
                never()
        ).embed(any());
    }

    @Test
    @DisplayName("공개된 snapshot이 없으면 채팅 수락 전 준비 검사가 실패한다")
    void requirePublishedSnapshot_failure_noPublishedSnapshot() {
        // given
        SearchChunkRepository repository = mock(SearchChunkRepository.class);
        when(repository.findPublishedImportRunId(7L)).thenReturn(Optional.empty());
        PublishedDocumentSearchService service = new PublishedDocumentSearchService(
                repository,
                mock(DocumentEmbeddingClient.class),
                new SearchQueryTerms(),
                new SearchQuestionClassifier(),
                PROPERTIES,
                new EmbeddingProperties(
                        "qwen-embedding",
                        1024
                )
        );

        // when
        ThrowingCallable action = () -> service.requirePublishedSnapshot(7L);

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_IMPORT_NOT_READY)
        );
    }

    @Test
    @DisplayName("범위가 넓은 질문은 snapshot이 있어도 모델 검색 대신 구체화를 요청한다")
    void search_success_broadQuestionNeedsClarification() {
        // given
        SearchChunkRepository repository = mock(SearchChunkRepository.class);
        DocumentEmbeddingClient embeddingClient = mock(DocumentEmbeddingClient.class);
        when(repository.findPublishedImportRunId(7L)).thenReturn(Optional.of(11L));
        PublishedDocumentSearchService service = new PublishedDocumentSearchService(
                repository,
                embeddingClient,
                new SearchQueryTerms(),
                new SearchQuestionClassifier(),
                PROPERTIES,
                new EmbeddingProperties(
                        "qwen-embedding",
                        1024
                )
        );

        // when
        SearchContext context = service.search(
                7L,
                "우리 프로젝트 어떻게 진행되고 있어?"
        );

        // then
        assertThat(context.fallbackAnswer()).contains("범위가 넓어요");
        verify(
                embeddingClient,
                never()
        ).embed(any());
        verify(
                repository,
                never()
        ).findByVector(
                any(),
                any(),
                any(),
                any(int.class)
        );
    }

    @Test
    @DisplayName("vector와 keyword 모두 결과가 없으면 근거 없음으로 분류한다")
    void search_success_noRelevantChunk() {
        // given
        SearchChunkRepository repository = mock(SearchChunkRepository.class);
        DocumentEmbeddingClient embeddingClient = mock(DocumentEmbeddingClient.class);
        when(repository.findPublishedImportRunId(7L)).thenReturn(Optional.of(11L));
        when(embeddingClient.embed(any())).thenReturn(List.of(new double[1024]));
        when(
                repository.findByVector(
                        any(),
                        any(),
                        any(),
                        any(int.class)
                )
        ).thenReturn(List.of());
        when(
                repository.findByKeywords(
                        any(),
                        any(),
                        any(),
                        any(int.class)
                )
        ).thenReturn(List.of());
        PublishedDocumentSearchService service = new PublishedDocumentSearchService(
                repository,
                embeddingClient,
                new SearchQueryTerms(),
                new SearchQuestionClassifier(),
                PROPERTIES,
                new EmbeddingProperties(
                        "qwen-embedding",
                        1024
                )
        );

        // when
        SearchContext context = service.search(
                7L,
                "Redis 결정 이유"
        );

        // then
        assertThat(context.isNoResult()).isTrue();
        assertThat(context.fallbackAnswer()).contains("찾지 못했습니다");
    }

    @Test
    @DisplayName("vector 후보가 최소 관련도보다 낮으면 근거 없음으로 분류한다")
    void search_success_ignoresLowRelevanceCandidates() {
        // given
        SearchChunkRepository repository = mock(SearchChunkRepository.class);
        DocumentEmbeddingClient embeddingClient = mock(DocumentEmbeddingClient.class);
        when(repository.findPublishedImportRunId(7L)).thenReturn(Optional.of(11L));
        when(embeddingClient.embed(List.of("무관한 질문"))).thenReturn(List.of(new double[1024]));
        when(
                repository.findByVector(
                        eq(7L),
                        eq(11L),
                        any(),
                        eq(50)
                )
        ).thenReturn(
                List.of(
                        chunk(
                                7L,
                                101L,
                                11L,
                                0,
                                "무관한 문서",
                                "무관한 내용",
                                0.1
                        )
                )
        );
        when(
                repository.findByKeywords(
                        eq(7L),
                        eq(11L),
                        any(),
                        eq(50)
                )
        ).thenReturn(List.of());
        PublishedDocumentSearchService service = new PublishedDocumentSearchService(
                repository,
                embeddingClient,
                new SearchQueryTerms(),
                new SearchQuestionClassifier(),
                PROPERTIES,
                new EmbeddingProperties(
                        "qwen-embedding",
                        1024
                )
        );

        // when
        SearchContext context = service.search(
                7L,
                "무관한 질문"
        );

        // then
        assertThat(context.isNoResult()).isTrue();
    }

    private static SearchChunk chunk(
            Long workspaceId,
            Long pageId,
            Long importRunId,
            int chunkIndex,
            String title,
            String content,
            double score
    ) {
        return SearchChunk.retrieved(
                workspaceId,
                pageId,
                importRunId,
                chunkIndex,
                title,
                "https://notion.test/" + title,
                Instant.parse("2026-09-01T00:00:00Z"),
                content,
                score
        );
    }
}
