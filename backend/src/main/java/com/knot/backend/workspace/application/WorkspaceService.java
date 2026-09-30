package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceCreateResult;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import com.knot.backend.member.domain.MemberRepository;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private static final int MAX_CREATED_WORKSPACES = 3;

    private final MemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final Clock clock;

    @Transactional
    public WorkspaceCreateResult create(
            long memberId,
            String name
    ) {
        Instant createdAt = Instant.now(clock);
        Workspace candidate = Workspace.create(
                name,
                memberId,
                createdAt
        );
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_CREATOR_NOT_FOUND));
        if (workspaceRepository.countActiveByCreatorId(memberId) >= MAX_CREATED_WORKSPACES) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_CREATION_LIMIT_EXCEEDED);
        }

        Workspace workspace = workspaceRepository.save(candidate);
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                workspace.getId(),
                memberId,
                WorkspaceMemberRole.OWNER,
                createdAt
        );
        workspaceMemberRepository.save(workspaceMember);

        return new WorkspaceCreateResult(workspace.getId());
    }
}
