package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.application.WorkspaceOwnershipTransferCandidateQuery;
import com.knot.backend.workspace.application.dto.result.WorkspaceOwnershipTransferCandidateResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WorkspaceOwnershipTransferCandidateQueryAdapter implements WorkspaceOwnershipTransferCandidateQuery {
    private final WorkspaceMemberJpaRepository workspaceMemberJpaRepository;

    @Override
    public List<WorkspaceOwnershipTransferCandidateResult> findAll(
            Long workspaceId,
            long ownerMemberId
    ) {
        return workspaceMemberJpaRepository.findActiveOwnershipTransferCandidates(
                workspaceId,
                ownerMemberId
        )
                .stream()
                .map(
                        candidate -> new WorkspaceOwnershipTransferCandidateResult(
                                candidate.memberId(),
                                candidate.nickname()
                        )
                )
                .toList();
    }
}
