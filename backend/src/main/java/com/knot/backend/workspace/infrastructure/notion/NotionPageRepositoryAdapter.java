package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.domain.NotionPageMetadata;
import com.knot.backend.workspace.domain.NotionPageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotionPageRepositoryAdapter implements NotionPageRepository {
    private final NotionPageJpaRepository notionPageJpaRepository;

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
