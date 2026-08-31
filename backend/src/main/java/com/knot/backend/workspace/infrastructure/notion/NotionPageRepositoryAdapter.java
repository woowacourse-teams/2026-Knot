package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.domain.NotionPage;
import com.knot.backend.workspace.domain.NotionPageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotionPageRepositoryAdapter implements NotionPageRepository {
    private final NotionPageJpaRepository notionPageJpaRepository;

    @Override
    public List<NotionPage> findAllByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId) {
        return notionPageJpaRepository.findAllByWorkspaceIdOrderByPositionAscIdAsc(workspaceId);
    }
}
