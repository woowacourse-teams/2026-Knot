package com.knot.backend.search.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.workspace.domain.ImportedPage;
import com.knot.backend.workspace.domain.ImportedPageRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchIndexingServiceTest {
    private static final SearchProperties PROPERTIES = new SearchProperties(
            100,
            10,
            50,
            3,
            10000
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

        // when & then
        assertThatThrownBy(
                () -> service.index(
                        11L,
                        7L
                )
        ).isInstanceOfSatisfying(
                SearchException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.searchErrorCode())
                        .isEqualTo(SearchErrorCode.SEARCH_PROVIDER_FAILED)
        );
        org.mockito.Mockito.verifyNoInteractions(persistenceService);
    }
}
