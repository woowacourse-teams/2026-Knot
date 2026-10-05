package com.knot.backend.workspace.application;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
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
public class WorkspaceDeletionService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final RecordingSessionRepository recordingSessionRepository;
    private final Clock clock;

    public void delete(
            long memberId,
            Long workspaceId
    ) {
        validateWorkspaceId(workspaceId);
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        List<WorkspaceMember> activeMembers = workspaceMemberRepository
                .findAllActiveByWorkspaceIdForUpdate(workspaceId);
        findActor(
                activeMembers,
                memberId
        ).validateCanDeleteWorkspace();

        Instant deletedAt = currentTime();
        workspace.delete(deletedAt);
        workspaceRepository.save(workspace);
        for (WorkspaceMember activeMember : activeMembers) {
            activeMember.leaveByWorkspaceDeletion(deletedAt);
        }
        workspaceMemberRepository.saveAll(activeMembers);
        discardActiveRecordings(
                workspaceId,
                deletedAt
        );
    }

    private WorkspaceMember findActor(
            List<WorkspaceMember> activeMembers,
            long memberId
    ) {
        for (WorkspaceMember activeMember : activeMembers) {
            if (activeMember.getMemberId() == memberId) {
                return activeMember;
            }
        }
        throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
    }

    private void discardActiveRecordings(
            long workspaceId,
            Instant discardedAt
    ) {
        List<RecordingSession> recordingSessions = recordingSessionRepository
                .findAllActiveByWorkspaceIdForUpdate(workspaceId);
        for (RecordingSession recordingSession : recordingSessions) {
            recordingSession.discard(discardedAt);
            recordingSessionRepository.save(recordingSession);
        }
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }
}
