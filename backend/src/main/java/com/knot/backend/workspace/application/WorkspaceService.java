package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceCreateResult;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final Clock clock;

    @Transactional
    public WorkspaceCreateResult create(
            long memberId,
            String name
    ) {
        Instant createdAt = Instant.now(clock);
        Workspace workspace = workspaceRepository.save(
                Workspace.create(
                        name,
                        createdAt
                )
        );
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
