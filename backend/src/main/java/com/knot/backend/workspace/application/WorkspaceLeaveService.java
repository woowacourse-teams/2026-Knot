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
public class WorkspaceLeaveService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final RecordingSessionRepository recordingSessionRepository;
    private final Clock clock;

    public void leave(
            long memberId,
            Long workspaceId
    ) {
        validateWorkspaceId(workspaceId);
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        WorkspaceMember actor = workspaceMemberRepository.findLatestByWorkspaceIdAndMemberIdForUpdate(
                workspaceId,
                memberId
        )
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));
        if (!actor.isActive()) {
            return;
        }

        long activeMemberCount = workspaceMemberRepository.countActiveByWorkspaceId(workspaceId);
        Instant leftAt = currentTime();
        actor.leave(
                leftAt,
                activeMemberCount
        );
        if (isLastActiveMember(activeMemberCount)) {
            workspace.delete(leftAt);
            workspaceRepository.save(workspace);
        }
        workspaceMemberRepository.save(actor);
        discardActiveRecordings(
                findActiveRecordings(
                        workspaceId,
                        memberId,
                        activeMemberCount
                ),
                leftAt
        );
    }

    private List<RecordingSession> findActiveRecordings(
            long workspaceId,
            long memberId,
            long activeMemberCount
    ) {
        if (isLastActiveMember(activeMemberCount)) {
            return recordingSessionRepository.findAllActiveByWorkspaceIdForUpdate(workspaceId);
        }
        return recordingSessionRepository.findAllActiveByWorkspaceIdAndMemberIdForUpdate(
                workspaceId,
                memberId
        );
    }

    private void discardActiveRecordings(
            List<RecordingSession> recordingSessions,
            Instant discardedAt
    ) {
        for (RecordingSession recordingSession : recordingSessions) {
            recordingSession.discard(discardedAt);
            recordingSessionRepository.save(recordingSession);
        }
    }

    private boolean isLastActiveMember(long activeMemberCount) {
        return activeMemberCount == 1;
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
