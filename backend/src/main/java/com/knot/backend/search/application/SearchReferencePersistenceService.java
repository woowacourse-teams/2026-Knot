package com.knot.backend.search.application;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchReferenceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchReferencePersistenceService {
    private final SearchReferenceRepository searchReferenceRepository;

    @Transactional
    public void replace(
            Long messageId,
            List<SearchChunk> references
    ) {
        searchReferenceRepository.replace(
                messageId,
                references
        );
    }
}
