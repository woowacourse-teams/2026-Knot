package com.knot.backend.workspace.infrastructure.persistence;

import com.knot.backend.workspace.domain.ImportedPageMetadata;
import com.knot.backend.workspace.domain.ImportedPage;
import com.knot.backend.workspace.domain.ImportedPageRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ImportedPageRepositoryAdapter implements ImportedPageRepository {
    private final ImportedPageJpaRepository importedPageJpaRepository;

    @Override
    public ImportedPage save(ImportedPage importedPage) {
        return importedPageJpaRepository.saveAndFlush(importedPage);
    }

    @Override
    public long countByWorkspaceIdAndImportRunId(
            Long workspaceId,
            Long importRunId
    ) {
        return importedPageJpaRepository.countByWorkspaceIdAndImportRunId(
                workspaceId,
                importRunId
        );
    }

    @Override
    public List<ImportedPage> findAllByWorkspaceIdAndImportRunIdOrderByPositionAscIdAsc(
            Long workspaceId,
            Long importRunId
    ) {
        return importedPageJpaRepository.findAllByWorkspaceIdAndImportRunIdOrderByPositionAscIdAsc(
                workspaceId,
                importRunId
        );
    }

    @Override
    public void publish(
            Long workspaceId,
            Long importRunId,
            Instant publishedAt
    ) {
        importedPageJpaRepository.publish(
                workspaceId,
                importRunId,
                publishedAt
        );
    }

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
