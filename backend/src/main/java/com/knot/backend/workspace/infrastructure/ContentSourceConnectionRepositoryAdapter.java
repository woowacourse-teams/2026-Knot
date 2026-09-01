package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ContentSourceConnectionRepositoryAdapter implements ContentSourceConnectionRepository {
    private final ContentSourceConnectionJpaRepository connectionJpaRepository;

    public ContentSourceConnectionRepositoryAdapter(ContentSourceConnectionJpaRepository connectionJpaRepository) {
        this.connectionJpaRepository = connectionJpaRepository;
    }

    @Override
    public ContentSourceConnection save(ContentSourceConnection connection) {
        return connectionJpaRepository.saveAndFlush(connection);
    }

    @Override
    public Optional<ContentSourceConnection> findByIdAndWorkspaceId(
            Long connectionId,
            Long workspaceId
    ) {
        return connectionJpaRepository.findByIdAndWorkspaceId(
                connectionId,
                workspaceId
        );
    }

    @Override
    public Optional<ContentSourceConnection> findByWorkspaceIdAndProvider(
            Long workspaceId,
            ContentSourceProvider provider
    ) {
        return connectionJpaRepository.findByWorkspaceIdAndProvider(
                workspaceId,
                provider
        );
    }

    @Override
    public Optional<ContentSourceConnection> findByWorkspaceIdAndProviderForUpdate(
            Long workspaceId,
            ContentSourceProvider provider
    ) {
        return connectionJpaRepository.findByWorkspaceIdAndProviderForUpdate(
                workspaceId,
                provider
        );
    }

    @Override
    public Optional<ContentSourceConnection> findByIdForUpdate(Long connectionId) {
        return connectionJpaRepository.findByIdForUpdate(connectionId);
    }
}
