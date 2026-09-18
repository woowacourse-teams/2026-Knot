package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceRepositoryAdapter implements WorkspaceRepository {
    private final WorkspaceJpaRepository workspaceJpaRepository;

    public WorkspaceRepositoryAdapter(WorkspaceJpaRepository workspaceJpaRepository) {
        this.workspaceJpaRepository = workspaceJpaRepository;
    }

    @Override
    public Workspace save(Workspace workspace) {
        return workspaceJpaRepository.save(workspace);
    }

    @Override
    public Optional<Workspace> findById(Long workspaceId) {
        return workspaceJpaRepository.findById(workspaceId);
    }

    @Override
    public Optional<Workspace> findByIdForUpdate(Long workspaceId) {
        return workspaceJpaRepository.findWithLockById(workspaceId);
    }

    @Override
    public List<Workspace> findAllByMemberId(Long memberId) {
        return workspaceJpaRepository.findAllByMemberId(memberId);
    }
}
