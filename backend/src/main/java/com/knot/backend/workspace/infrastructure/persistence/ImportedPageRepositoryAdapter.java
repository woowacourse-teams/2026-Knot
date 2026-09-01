package com.knot.backend.workspace.infrastructure.persistence;

import com.knot.backend.workspace.domain.ImportedPageMetadata;
import com.knot.backend.workspace.domain.ImportedPageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ImportedPageRepositoryAdapter implements ImportedPageRepository {
    private final ImportedPageJpaRepository importedPageJpaRepository;

    @Override
    public List<ImportedPageMetadata> findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId) {
        return importedPageJpaRepository.findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(workspaceId)
                .stream()
                .map(
                        projection -> new ImportedPageMetadata(
                                projection.getId(),
                                projection.getWorkspaceId(),
                                projection.getParentId(),
                                projection.getHasParentReference(),
                                projection.getTitle(),
                                projection.getPosition(),
                                projection.getSourceUrl()
                        )
                )
                .toList();
    }
}
