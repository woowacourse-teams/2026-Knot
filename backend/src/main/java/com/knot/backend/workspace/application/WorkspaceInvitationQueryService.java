package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationSummaryResult;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceInvitationQueryService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInvitationRepository invitationRepository;
    private final Clock clock;

    public List<WorkspaceInvitationSummaryResult> findAll(
            Long workspaceId,
            long memberId
    ) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
        workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                workspaceId,
                memberId,
                WorkspaceMemberRole.OWNER
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        }
        Instant now = Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
        return invitationRepository.findValidByWorkspaceIdAt(
                workspaceId,
                now
        )
                .stream()
                .map(
                        invitation -> new WorkspaceInvitationSummaryResult(
                                invitation.getId(),
                                invitation.getCreatedAt(),
                                invitation.getExpiresAt()
                        )
                )
                .toList();
    }
}
