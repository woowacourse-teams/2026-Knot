package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceOwnershipTransferCandidateResult;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceOwnershipTransferCandidateService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceOwnershipTransferCandidateQuery candidateQuery;

    public List<WorkspaceOwnershipTransferCandidateResult> findCandidates(
            Long workspaceId,
            long memberId
    ) {
        validateWorkspaceId(workspaceId);
        workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateOwner(
                workspaceId,
                memberId
        );
        return candidateQuery.findAll(
                workspaceId,
                memberId
        );
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }

    private void validateOwner(
            Long workspaceId,
            long memberId
    ) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                workspaceId,
                memberId,
                WorkspaceMemberRole.OWNER
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        }
    }
}
