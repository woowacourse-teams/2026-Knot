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
        return workspaceMemberJpaRepository.findAllActiveByMemberIdForUpdate(memberId);
    }

    @Override
    public List<WorkspaceMember> findAllActiveByWorkspaceIdAndMemberIdsForUpdate(
            Long workspaceId,
            List<Long> memberIds
    ) {
        return workspaceMemberJpaRepository.findAllActiveByWorkspaceIdAndMemberIdsForUpdate(
                workspaceId,
                memberIds
        );
    }

    @Override
    public Optional<WorkspaceMember> findLastViewedByMemberId(Long memberId) {
        return workspaceMemberJpaRepository.findActiveLastViewedByMemberId(memberId);
    }

    @Override
    public Optional<WorkspaceMember> findLatestByWorkspaceIdAndMemberIdForUpdate(
            Long workspaceId,
            Long memberId
    ) {
        return workspaceMemberJpaRepository.findFirstByWorkspaceIdAndMemberIdOrderByIdDesc(
                workspaceId,
                memberId
        );
    }

    @Override
    public long countActiveByWorkspaceId(Long workspaceId) {
        return workspaceMemberJpaRepository.countActiveByWorkspaceId(workspaceId);
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
        return workspaceMemberJpaRepository.existsActiveByWorkspaceIdAndMemberId(
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
        return workspaceMemberJpaRepository.existsActiveByWorkspaceIdAndMemberIdAndRole(
                workspaceId,
                memberId,
                role
        );
    }
}
