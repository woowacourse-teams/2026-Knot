package com.knot.backend.workspace.application;

import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceLastViewedService {
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public void update(
            long memberId,
            Long workspaceId
    ) {
        validateWorkspaceId(workspaceId);
        List<WorkspaceMember> workspaceMembers = workspaceMemberRepository.findAllByMemberIdForUpdate(memberId);
        WorkspaceMember target = findTarget(
                workspaceMembers,
                workspaceId
        );

        workspaceMembers.forEach(WorkspaceMember::clearLastViewed);
        workspaceMemberRepository.saveAll(workspaceMembers);
        workspaceMemberRepository.flush();

        target.markLastViewed();
        workspaceMemberRepository.save(target);
    }

    private WorkspaceMember findTarget(
            List<WorkspaceMember> workspaceMembers,
            Long workspaceId
    ) {
        return workspaceMembers.stream()
                .filter(
                        workspaceMember -> workspaceMember.getWorkspaceId()
                                .equals(workspaceId)
                )
                .findFirst()
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }
}
