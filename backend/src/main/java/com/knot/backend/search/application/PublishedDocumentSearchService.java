package com.knot.backend.search.application;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchChunkRepository;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublishedDocumentSearchService {
    private static final double VECTOR_WEIGHT = 0.7;
    private static final double KEYWORD_WEIGHT = 0.3;

    private final SearchChunkRepository searchChunkRepository;
    private final DocumentEmbeddingClient embeddingClient;
    private final SearchQueryTerms queryTerms;
    private final SearchQuestionClassifier questionClassifier;
    private final SearchProperties properties;
    private final EmbeddingProperties embeddingProperties;

    public SearchContext search(
            Long workspaceId,
            String query
    ) {
        return search(
                workspaceId,
                query,
                query
        );
    }

    public SearchContext search(
            Long workspaceId,
            String query,
            String searchQuery
    ) {
        validate(
                workspaceId,
                query
        );
        validateQuery(searchQuery);
        Long publishedImportRunId = requirePublishedImportRunId(workspaceId);
        if (questionClassifier.isBroad(query)) {
            return SearchContext.needsClarification();
        }

        List<String> terms = queryTerms.extract(searchQuery);
        List<SearchChunk> vectorCandidates = embedAndSearch(
                workspaceId,
                publishedImportRunId,
                searchQuery
        );
        List<SearchChunk> keywordCandidates = terms.isEmpty()
                ? List.of()
                : searchChunkRepository.findByKeywords(
                        workspaceId,
                        publishedImportRunId,
                        terms,
                        properties.candidateLimit()
                );
        List<SearchChunk> selected = selectSources(
                vectorCandidates,
                keywordCandidates
        );
        if (selected.isEmpty()) {
            return SearchContext.noResult();
        }
        return SearchContext.ready(
                selected,
                properties.maxContextCharacters()
        );
    }

    public void requirePublishedSnapshot(Long workspaceId) {
        validateWorkspaceId(workspaceId);
        requirePublishedImportRunId(workspaceId);
    }

    private List<SearchChunk> embedAndSearch(
            Long workspaceId,
            Long importRunId,
            String query
    ) {
        try {
            List<double[]> embeddings = embeddingClient.embed(List.of(query));
            if (embeddings == null || embeddings.size() != 1 || embeddings.getFirst() == null
                    || embeddings.getFirst().length != embeddingProperties.dimensions()) {
                throw new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED);
            }
            return searchChunkRepository.findByVector(
                    workspaceId,
                    importRunId,
                    embeddings.getFirst(),
                    properties.candidateLimit()
            );
        } catch (SearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SearchException(
                    SearchErrorCode.SEARCH_PROVIDER_FAILED,
                    exception
            );
        }
    }

    private List<SearchChunk> selectSources(
            List<SearchChunk> vectorCandidates,
            List<SearchChunk> keywordCandidates
    ) {
        Map<String, SearchChunk> selectedByChunk = new HashMap<>();
        Map<String, Double> scoresByChunk = new HashMap<>();
        for (SearchChunk candidate : vectorCandidates) {
            if (!isRelevant(candidate)) {
                continue;
            }
            String key = key(candidate);
            selectedByChunk.put(
                    key,
                    candidate
            );
            scoresByChunk.merge(
                    key,
                    normalize(candidate.score()) * VECTOR_WEIGHT,
                    Double::sum
            );
        }
        for (SearchChunk candidate : keywordCandidates) {
            if (!isRelevant(candidate)) {
                continue;
            }
            String key = key(candidate);
            selectedByChunk.putIfAbsent(
                    key,
                    candidate
            );
            scoresByChunk.merge(
                    key,
                    normalize(candidate.score()) * KEYWORD_WEIGHT,
                    Double::sum
            );
        }
        List<SearchChunk> rankedChunks = selectedByChunk.entrySet()
                .stream()
                .map(
                        entry -> entry.getValue()
                                .withScore(
                                        Math.min(
                                                scoresByChunk.get(entry.getKey()),
                                                1.0
                                        )
                                )
                )
                .sorted(
                        Comparator.comparingDouble(SearchChunk::score)
                                .reversed()
                                .thenComparing(SearchChunk::title)
                                .thenComparingInt(SearchChunk::chunkIndex)
                )
                .toList();
        List<SearchChunk> sources = new ArrayList<>();
        Set<Long> pageIds = new HashSet<>();
        for (SearchChunk chunk : rankedChunks) {
            if (pageIds.add(chunk.importedPageId())) {
                sources.add(chunk);
            }
            if (sources.size() >= properties.topK()) {
                break;
            }
        }
        return List.copyOf(sources);
    }

    private boolean isRelevant(SearchChunk candidate) {
        return normalize(candidate.score()) >= properties.minimumRelevanceScore();
    }

    private String key(SearchChunk chunk) {
        return chunk.importedPageId() + ":" + chunk.chunkIndex();
    }

    private double normalize(double score) {
        return Math.max(
                0,
                Math.min(
                        score,
                        1
                )
        );
    }

    private void validate(
            Long workspaceId,
            String query
    ) {
        properties.validate();
        embeddingProperties.validate();
        if (embeddingProperties.dimensions() != 1024) {
            throw new SearchException(SearchErrorCode.SEARCH_CONFIGURATION_INVALID);
        }
        validateWorkspaceId(workspaceId);
        validateQuery(query);
    }

    private Long requirePublishedImportRunId(Long workspaceId) {
        return searchChunkRepository.findPublishedImportRunId(workspaceId)
                .orElseThrow(() -> new SearchException(SearchErrorCode.SEARCH_IMPORT_NOT_READY));
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new SearchException(SearchErrorCode.INVALID_SEARCH_WORKSPACE_ID);
        }
    }

    private void validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new SearchException(SearchErrorCode.INVALID_SEARCH_QUERY);
        }
    }
}
