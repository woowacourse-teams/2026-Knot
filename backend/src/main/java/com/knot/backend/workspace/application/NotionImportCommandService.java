package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.NotionImportRunRequestResult;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.domain.NotionImportErrorCode;
import com.knot.backend.workspace.domain.NotionImportException;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotionImportCommandService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ContentSourceConnectionRepository connectionRepository;
    private final NotionImportRunRepository importRunRepository;
    private final Clock clock;

    @Transactional
    public NotionImportRunRequestResult start(
            Long workspaceId,
            long memberId
    ) {
        validateWorkspaceId(workspaceId);
        validateWorkspaceExists(workspaceId);
        validateOwner(
                workspaceId,
                memberId
        );
        ContentSourceConnection connection = connectedNotionConnectionForUpdate(workspaceId);
        validateConnectionAuthorization(connection);
        return importRunRepository.findActiveByContentSourceConnectionId(connection.getId())
                .map(this::existingResult)
                .orElseGet(
                        () -> createPendingRun(
                                workspaceId,
                                connection.getId(),
                                memberId
                        )
                );
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }

    private void validateWorkspaceExists(Long workspaceId) {
        workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
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

    private ContentSourceConnection connectedNotionConnectionForUpdate(Long workspaceId) {
        return connectionRepository.findByWorkspaceIdAndProviderForUpdate(
                workspaceId,
                ContentSourceProvider.NOTION
        )
                .orElseThrow(() -> new NotionImportException(NotionImportErrorCode.NOTION_CONNECTION_NOT_CONNECTED));
    }

    private void validateConnectionAuthorization(ContentSourceConnection connection) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                connection.getWorkspaceId(),
                connection.getAuthorizingMemberId(),
                WorkspaceMemberRole.OWNER
        )) {
            throw new NotionImportException(NotionImportErrorCode.NOTION_CONNECTION_REAUTHENTICATION_REQUIRED);
        }
    }

    private NotionImportRunRequestResult existingResult(NotionImportRun importRun) {
        return new NotionImportRunRequestResult(
                importRun.getId(),
                false
        );
    }

    private NotionImportRunRequestResult createPendingRun(
            Long workspaceId,
            Long connectionId,
            long memberId
    ) {
        NotionImportRun importRun = NotionImportRun.createPending(
                workspaceId,
                connectionId,
                memberId,
                currentTime()
        );
        NotionImportRun savedImportRun = importRunRepository.save(importRun);
        return new NotionImportRunRequestResult(
                savedImportRun.getId(),
                true
        );
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }
}
