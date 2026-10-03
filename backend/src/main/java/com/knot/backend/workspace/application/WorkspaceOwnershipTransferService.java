package com.knot.backend.workspace.application;

import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMember;
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
@Transactional
public class WorkspaceOwnershipTransferService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final Clock clock;

    public void transferOwnership(
            long memberId,
            Long workspaceId,
            long successorMemberId
    ) {
        validateWorkspaceId(workspaceId);
        validateSuccessor(
                memberId,
                successorMemberId
        );
        workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        List<WorkspaceMember> participants = workspaceMemberRepository.findAllActiveByWorkspaceIdAndMemberIdsForUpdate(
                workspaceId,
                List.of(
                        memberId,
                        successorMemberId
                )
        );
        WorkspaceMember owner = findOwner(
                participants,
                memberId
        );
        WorkspaceMember successor = findSuccessor(
                participants,
                successorMemberId
        );
        Instant leftAt = Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
        successor.receiveOwnership();
        owner.leaveAfterOwnershipTransfer(
                successor,
                leftAt
        );
        workspaceMemberRepository.saveAll(
                List.of(
                        owner,
                        successor
                )
        );
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }

    private void validateSuccessor(
            long memberId,
            long successorMemberId
    ) {
        if (memberId == successorMemberId) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_OWNERSHIP_TRANSFER_TARGET);
        }
    }

    private WorkspaceMember findOwner(
            List<WorkspaceMember> participants,
            long memberId
    ) {
        return participants.stream()
                .filter(participant -> participant.getMemberId() == memberId)
                .filter(participant -> participant.getRole() == WorkspaceMemberRole.OWNER)
                .findFirst()
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED));
    }

    private WorkspaceMember findSuccessor(
            List<WorkspaceMember> participants,
            long successorMemberId
    ) {
        return participants.stream()
                .filter(participant -> participant.getMemberId() == successorMemberId)
                .findFirst()
                .orElseThrow(
                        () -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNERSHIP_TRANSFER_TARGET_CONFLICT)
                );
    }
}
