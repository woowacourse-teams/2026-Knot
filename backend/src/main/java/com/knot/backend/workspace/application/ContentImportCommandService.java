package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ContentImportRunRequestResult;
import com.knot.backend.workspace.domain.ContentImportErrorCode;
import com.knot.backend.workspace.domain.ContentImportException;
import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRunRepository;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
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
public class ContentImportCommandService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ContentSourceConnectionRepository connectionRepository;
    private final ContentImportRunRepository importRunRepository;
    private final Clock clock;

    @Transactional
    public ContentImportRunRequestResult start(
            Long workspaceId,
            long memberId,
            ContentSourceProvider provider
    ) {
        validateWorkspaceId(workspaceId);
        validateWorkspaceExists(workspaceId);
        validateOwner(
                workspaceId,
                memberId
        );
        ContentSourceConnection connection = connectedContentSourceConnectionForUpdate(
                workspaceId,
                provider
        );
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

    private ContentSourceConnection connectedContentSourceConnectionForUpdate(
            Long workspaceId,
            ContentSourceProvider provider
    ) {
        return connectionRepository.findByWorkspaceIdAndProviderForUpdate(
                workspaceId,
                provider
        )
                .orElseThrow(
                        () -> new ContentImportException(ContentImportErrorCode.CONTENT_SOURCE_CONNECTION_NOT_CONNECTED)
                );
    }

    private void validateConnectionAuthorization(ContentSourceConnection connection) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                connection.getWorkspaceId(),
                connection.getAuthorizingMemberId(),
                WorkspaceMemberRole.OWNER
        )) {
            throw new ContentImportException(
                    ContentImportErrorCode.CONTENT_SOURCE_CONNECTION_REAUTHENTICATION_REQUIRED
            );
        }
    }

    private ContentImportRunRequestResult existingResult(ContentImportRun importRun) {
        return new ContentImportRunRequestResult(
                importRun.getId(),
                false
        );
    }

    private ContentImportRunRequestResult createPendingRun(
            Long workspaceId,
            Long connectionId,
            long memberId
    ) {
        ContentImportRun importRun = ContentImportRun.createPending(
                workspaceId,
                connectionId,
                memberId,
                currentTime()
        );
        ContentImportRun savedImportRun = importRunRepository.save(importRun);
        return new ContentImportRunRequestResult(
                savedImportRun.getId(),
                true
        );
    }

    private Instant currentTime() {
        return Instant.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }
}
