package com.knot.backend.search.application;

import com.knot.backend.search.domain.SearchChunkRepository;
import com.knot.backend.search.domain.SearchIndexedChunk;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchIndexPersistenceService {
    private final SearchChunkRepository searchChunkRepository;

    @Transactional
    public void replace(
            Long workspaceId,
            Long importRunId,
            List<SearchIndexedChunk> chunks
    ) {
        searchChunkRepository.replace(
                workspaceId,
                importRunId,
                chunks
        );
    }
}
