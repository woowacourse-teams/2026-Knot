package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceMemberRepositoryAdapter implements WorkspaceMemberRepository {
    private final WorkspaceMemberJpaRepository workspaceMemberJpaRepository;

    public WorkspaceMemberRepositoryAdapter(WorkspaceMemberJpaRepository workspaceMemberJpaRepository) {
        this.workspaceMemberJpaRepository = workspaceMemberJpaRepository;
    }

    @Override
    public WorkspaceMember save(WorkspaceMember workspaceMember) {
        return workspaceMemberJpaRepository.save(workspaceMember);
    }

    @Override
    public Optional<WorkspaceMember> findById(Long workspaceMemberId) {
        return workspaceMemberJpaRepository.findById(workspaceMemberId);
    }

    @Override
    public List<WorkspaceMember> findAllByMemberIdForUpdate(Long memberId) {
        return workspaceMemberJpaRepository.findAllByMemberIdOrderByIdAsc(memberId);
    }

    @Override
    public Optional<WorkspaceMember> findLastViewedByMemberId(Long memberId) {
        return workspaceMemberJpaRepository.findByMemberIdAndLastViewedTrue(memberId);
    }

    @Override
    public List<WorkspaceMember> saveAll(List<WorkspaceMember> workspaceMembers) {
        return workspaceMemberJpaRepository.saveAll(workspaceMembers);
    }

    @Override
    public void flush() {
        workspaceMemberJpaRepository.flush();
    }

    @Override
    public boolean existsByWorkspaceIdAndMemberId(
            Long workspaceId,
            Long memberId
    ) {
        return workspaceMemberJpaRepository.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        );
    }

    @Override
    public boolean existsByWorkspaceIdAndMemberIdAndRole(
            Long workspaceId,
            Long memberId,
            WorkspaceMemberRole role
    ) {
        return workspaceMemberJpaRepository.existsByWorkspaceIdAndMemberIdAndRole(
                workspaceId,
                memberId,
                role
        );
    }
}
