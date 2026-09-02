package com.knot.backend.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.search.domain.SearchIndexedChunk;
import com.knot.backend.workspace.domain.ImportedPage;
import com.knot.backend.workspace.domain.ImportedPageRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchIndexingServiceTest {
    private static final SearchProperties PROPERTIES = new SearchProperties(
            100,
            10,
            50,
            3,
            10000,
            2,
            0.35
    );

    @Test
    @DisplayName("Import page를 청크와 임베딩으로 변환하고 run 단위로 교체한다")
    void index_success_replacesRunChunks() {
        // given
        ImportedPageRepository pageRepository = mock(ImportedPageRepository.class);
        MarkdownChunker chunker = mock(MarkdownChunker.class);
        DocumentEmbeddingClient embeddingClient = mock(DocumentEmbeddingClient.class);
        SearchIndexPersistenceService persistenceService = mock(SearchIndexPersistenceService.class);
        ImportedPage page = mock(ImportedPage.class);
        when(page.getId()).thenReturn(101L);
        when(page.getTitle()).thenReturn("기술 스택");
        when(page.getMarkdownContent()).thenReturn("PostgreSQL을 사용한다");
        when(
                pageRepository.findAllByWorkspaceIdAndImportRunIdOrderByPositionAscIdAsc(
                        7L,
                        11L
                )
        ).thenReturn(List.of(page));
        when(chunker.chunk("PostgreSQL을 사용한다")).thenReturn(
                List.of(
                        new MarkdownChunk(
                                0,
                                "PostgreSQL을 사용한다"
                        )
                )
        );
        when(embeddingClient.embed(any())).thenReturn(List.of(new double[1024]));
        SearchIndexingService service = new SearchIndexingService(
                pageRepository,
                chunker,
                embeddingClient,
                persistenceService,
                PROPERTIES,
                new EmbeddingProperties(
                        "qwen-embedding",
                        1024
                )
        );

        // when
        service.index(
                11L,
                7L
        );

        // then
        verify(persistenceService).replace(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(11L),
                any()
        );
    }

    @Test
    @DisplayName("임베딩 차원이 pgvector 계약과 다르면 색인을 공개하지 않는다")
    void index_failure_invalidEmbeddingDimension() {
        // given
        ImportedPageRepository pageRepository = mock(ImportedPageRepository.class);
        MarkdownChunker chunker = mock(MarkdownChunker.class);
        DocumentEmbeddingClient embeddingClient = mock(DocumentEmbeddingClient.class);
        SearchIndexPersistenceService persistenceService = mock(SearchIndexPersistenceService.class);
        ImportedPage page = mock(ImportedPage.class);
        when(page.getId()).thenReturn(101L);
        when(page.getTitle()).thenReturn("기술 스택");
        when(page.getMarkdownContent()).thenReturn("본문");
        when(
                pageRepository.findAllByWorkspaceIdAndImportRunIdOrderByPositionAscIdAsc(
                        7L,
                        11L
                )
        ).thenReturn(List.of(page));
        when(chunker.chunk("본문")).thenReturn(
                List.of(
                        new MarkdownChunk(
                                0,
                                "본문"
                        )
                )
        );
        when(embeddingClient.embed(any())).thenReturn(List.of(new double[3]));
        SearchIndexingService service = new SearchIndexingService(
                pageRepository,
                chunker,
                embeddingClient,
                persistenceService,
                PROPERTIES,
                new EmbeddingProperties(
                        "qwen-embedding",
                        1024
                )
        );

        // when
        ThrowingCallable action = () -> service.index(
                11L,
                7L
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.searchErrorCode())
                        .isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );
        org.mockito.Mockito.verifyNoInteractions(persistenceService);
    }

    @Test
    @DisplayName("임베딩 요청을 배치로 나누고 원래 청크 순서대로 색인한다")
    void index_success_batchesEmbeddingsAndPreservesOrder() {
        // given
        ImportedPageRepository pageRepository = mock(ImportedPageRepository.class);
        MarkdownChunker chunker = mock(MarkdownChunker.class);
        DocumentEmbeddingClient embeddingClient = mock(DocumentEmbeddingClient.class);
        SearchIndexPersistenceService persistenceService = mock(SearchIndexPersistenceService.class);
        ImportedPage page = mock(ImportedPage.class);
        when(page.getId()).thenReturn(101L);
        when(page.getTitle()).thenReturn("기술 스택");
        when(page.getMarkdownContent()).thenReturn("본문");
        when(
                pageRepository.findAllByWorkspaceIdAndImportRunIdOrderByPositionAscIdAsc(
                        7L,
                        11L
                )
        ).thenReturn(List.of(page));
        when(chunker.chunk("본문")).thenReturn(
                List.of(
                        new MarkdownChunk(
                                0,
                                "첫 청크"
                        ),
                        new MarkdownChunk(
                                1,
                                "둘째 청크"
                        ),
                        new MarkdownChunk(
                                2,
                                "셋째 청크"
                        )
                )
        );
        when(
                embeddingClient.embed(
                        List.of(
                                "제목: 기술 스택\n첫 청크",
                                "제목: 기술 스택\n둘째 청크"
                        )
                )
        ).thenReturn(
                List.of(
                        vector(1),
                        vector(2)
                )
        );
        when(embeddingClient.embed(List.of("제목: 기술 스택\n셋째 청크"))).thenReturn(List.of(vector(3)));
        SearchIndexingService service = new SearchIndexingService(
                pageRepository,
                chunker,
                embeddingClient,
                persistenceService,
                PROPERTIES,
                new EmbeddingProperties(
                        "qwen-embedding",
                        1024
                )
        );
        AtomicReference<List<SearchIndexedChunk>> indexedChunksReference = new AtomicReference<>();
        doAnswer(invocation -> {
            indexedChunksReference.set(invocation.getArgument(2));
            return null;
        }).when(persistenceService)
                .replace(
                        any(),
                        any(),
                        any()
                );

        // when
        service.index(
                11L,
                7L
        );

        // then
        org.mockito.Mockito.verify(embeddingClient)
                .embed(
                        List.of(
                                "제목: 기술 스택\n첫 청크",
                                "제목: 기술 스택\n둘째 청크"
                        )
                );
        org.mockito.Mockito.verify(embeddingClient)
                .embed(List.of("제목: 기술 스택\n셋째 청크"));
        org.mockito.Mockito.verify(persistenceService)
                .replace(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(11L),
                        any()
                );
        List<SearchIndexedChunk> indexedChunks = indexedChunksReference.get();
        assertThat(indexedChunks).extracting(SearchIndexedChunk::content)
                .containsExactly(
                        "첫 청크",
                        "둘째 청크",
                        "셋째 청크"
                );
        assertThat(
                indexedChunks.get(0)
                        .embedding()
        ).containsExactly(vector(1));
        assertThat(
                indexedChunks.get(1)
                        .embedding()
        ).containsExactly(vector(2));
        assertThat(
                indexedChunks.get(2)
                        .embedding()
        ).containsExactly(vector(3));
    }

    @Test
    @DisplayName("중간 배치가 실패하면 모든 배치가 끝날 때까지 색인을 공개하지 않는다")
    void index_failure_lateEmbeddingBatchDoesNotPublish() {
        // given
        ImportedPageRepository pageRepository = mock(ImportedPageRepository.class);
        MarkdownChunker chunker = mock(MarkdownChunker.class);
        DocumentEmbeddingClient embeddingClient = mock(DocumentEmbeddingClient.class);
        SearchIndexPersistenceService persistenceService = mock(SearchIndexPersistenceService.class);
        ImportedPage page = mock(ImportedPage.class);
        when(page.getId()).thenReturn(101L);
        when(page.getTitle()).thenReturn("기술 스택");
        when(page.getMarkdownContent()).thenReturn("본문");
        when(
                pageRepository.findAllByWorkspaceIdAndImportRunIdOrderByPositionAscIdAsc(
                        7L,
                        11L
                )
        ).thenReturn(List.of(page));
        when(chunker.chunk("본문")).thenReturn(
                List.of(
                        new MarkdownChunk(
                                0,
                                "첫 청크"
                        ),
                        new MarkdownChunk(
                                1,
                                "둘째 청크"
                        ),
                        new MarkdownChunk(
                                2,
                                "셋째 청크"
                        )
                )
        );
        when(embeddingClient.embed(any())).thenAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            if (batch.size() == 2) {
                return List.of(
                        vector(1),
                        vector(2)
                );
            }
            return List.of(new double[3]);
        });
        SearchIndexingService service = new SearchIndexingService(
                pageRepository,
                chunker,
                embeddingClient,
                persistenceService,
                PROPERTIES,
                new EmbeddingProperties(
                        "qwen-embedding",
                        1024
                )
        );

        // when
        ThrowingCallable action = () -> service.index(
                11L,
                7L
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.searchErrorCode())
                        .isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );
        org.mockito.Mockito.verifyNoInteractions(persistenceService);
    }

    private static double[] vector(int activeIndex) {
        double[] vector = new double[1024];
        vector[activeIndex] = 1;
        return vector;
    }
}
