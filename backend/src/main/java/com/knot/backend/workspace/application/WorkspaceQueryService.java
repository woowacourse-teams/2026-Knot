package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceDetailResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceListResult;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceQueryService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceDetailResult findDetail(
            Long workspaceId,
            Long memberId
    ) {
        validateWorkspaceId(workspaceId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateWorkspaceMember(
                workspaceId,
                memberId
        );
        return WorkspaceDetailResult.from(workspace);
    }

    public WorkspaceListResult findAllByMemberId(long memberId) {
        List<Workspace> workspaces = workspaceRepository.findAllByMemberId(memberId);
        Long lastViewedWorkspaceId = workspaceMemberRepository.findLastViewedByMemberId(memberId)
                .map(WorkspaceMember::getWorkspaceId)
                .filter(
                        workspaceId -> containsWorkspace(
                                workspaces,
                                workspaceId
                        )
                )
                .orElse(null);
        return WorkspaceListResult.from(
                lastViewedWorkspaceId,
                workspaces
        );
    }

    private boolean containsWorkspace(
            List<Workspace> workspaces,
            Long workspaceId
    ) {
        return workspaces.stream()
                .anyMatch(
                        workspace -> workspace.getId()
                                .equals(workspaceId)
                );
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }

    private void validateWorkspaceMember(
            Long workspaceId,
            Long memberId
    ) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }
}
