package com.knot.backend.search.application;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.search.domain.SearchIndexedChunk;
import com.knot.backend.workspace.application.ContentImportSearchIndexer;
import com.knot.backend.workspace.domain.ImportedPage;
import com.knot.backend.workspace.domain.ImportedPageRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchIndexingService implements ContentImportSearchIndexer {
    private final ImportedPageRepository importedPageRepository;
    private final MarkdownChunker markdownChunker;
    private final DocumentEmbeddingClient embeddingClient;
    private final SearchIndexPersistenceService persistenceService;
    private final SearchProperties properties;
    private final EmbeddingProperties embeddingProperties;

    @Override
    public void index(
            Long importRunId,
            Long workspaceId
    ) {
        properties.validate();
        embeddingProperties.validate();
        if (embeddingProperties.dimensions() != 1024) {
            throw new SearchException(SearchErrorCode.SEARCH_CONFIGURATION_INVALID);
        }
        try {
            List<ImportedPage> pages = importedPageRepository.findAllByWorkspaceIdAndImportRunIdOrderByPositionAscIdAsc(
                    workspaceId,
                    importRunId
            );
            List<SearchChunkInput> inputs = flatten(pages);
            List<double[]> embeddings = embeddingClient.embed(
                    inputs.stream()
                            .map(SearchChunkInput::embeddingText)
                            .toList()
            );
            validateEmbeddings(
                    inputs,
                    embeddings
            );
            List<SearchIndexedChunk> indexedChunks = new ArrayList<>();
            for (int index = 0; index < inputs.size(); index++) {
                SearchChunkInput input = inputs.get(index);
                indexedChunks.add(
                        SearchIndexedChunk.of(
                                input.pageId(),
                                importRunId,
                                input.chunkIndex(),
                                input.content(),
                                embeddings.get(index)
                        )
                );
            }
            persistenceService.replace(
                    workspaceId,
                    importRunId,
                    indexedChunks
            );
        } catch (SearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SearchException(
                    SearchErrorCode.SEARCH_INDEX_FAILED,
                    exception
            );
        }
    }

    private List<SearchChunkInput> flatten(List<ImportedPage> pages) {
        List<SearchChunkInput> inputs = new ArrayList<>();
        for (ImportedPage page : pages) {
            for (MarkdownChunk chunk : markdownChunker.chunk(page.getMarkdownContent())) {
                inputs.add(
                        new SearchChunkInput(
                                page.getId(),
                                chunk.index(),
                                chunk.content(),
                                "제목: " + page.getTitle() + "\n" + chunk.content()
                        )
                );
            }
        }
        return inputs;
    }

    private void validateEmbeddings(
            List<SearchChunkInput> inputs,
            List<double[]> embeddings
    ) {
        if (embeddings == null || embeddings.size() != inputs.size()) {
            throw new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED);
        }
        for (double[] embedding : embeddings) {
            if (embedding == null || embedding.length != embeddingProperties.dimensions()) {
                throw new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED);
            }
        }
    }

}
