package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.domain.NotionPageMetadata;
import com.knot.backend.workspace.domain.NotionPageRepository;
import com.knot.backend.workspace.domain.NotionPage;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotionPageRepositoryAdapter implements NotionPageRepository {
    private final NotionPageJpaRepository notionPageJpaRepository;

    @Override
    public NotionPage save(NotionPage notionPage) {
        return notionPageJpaRepository.saveAndFlush(notionPage);
    }

    @Override
    public long countByWorkspaceIdAndImportRunId(
            Long workspaceId,
            Long importRunId
    ) {
        return notionPageJpaRepository.countByWorkspaceIdAndImportRunId(
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
        notionPageJpaRepository.publish(
                workspaceId,
                importRunId,
                publishedAt
        );
    }

    @Override
    public List<NotionPageMetadata> findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId) {
        return notionPageJpaRepository.findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(workspaceId)
                .stream()
                .map(
                        projection -> new NotionPageMetadata(
                                projection.getId(),
                                projection.getWorkspaceId(),
                                projection.getParentPageId(),
                                projection.getTitle(),
                                projection.getPosition(),
                                projection.getNotionUrl()
                        )
                )
                .toList();
    }
}
