package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.NotionConnection;
import com.knot.backend.workspace.domain.NotionConnectionRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class NotionConnectionRepositoryAdapter implements NotionConnectionRepository {
    private final NotionConnectionJpaRepository notionConnectionJpaRepository;

    public NotionConnectionRepositoryAdapter(NotionConnectionJpaRepository notionConnectionJpaRepository) {
        this.notionConnectionJpaRepository = notionConnectionJpaRepository;
    }

    @Override
    public NotionConnection save(NotionConnection connection) {
        return notionConnectionJpaRepository.saveAndFlush(connection);
    }

    @Override
    public Optional<NotionConnection> findByWorkspaceId(Long workspaceId) {
        return notionConnectionJpaRepository.findByWorkspaceId(workspaceId);
    }

    @Override
    public Optional<NotionConnection> findByWorkspaceIdForUpdate(Long workspaceId) {
        return notionConnectionJpaRepository.findByWorkspaceIdForUpdate(workspaceId);
    }
}
