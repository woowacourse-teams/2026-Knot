package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.NotionImportRunRequestResult;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.domain.NotionImportErrorCode;
import com.knot.backend.workspace.domain.NotionImportException;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import com.knot.backend.workspace.domain.NotionImportStatus;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotionImportCommandServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final long AUTHORIZING_MEMBER_ID = 3L;
    private static final Long CONNECTION_ID = 4L;
    private static final Long IMPORT_RUN_ID = 5L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00.123456Z");

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final ContentSourceConnectionRepository connectionRepository = mock(
            ContentSourceConnectionRepository.class
    );
    private final NotionImportRunRepository importRunRepository = mock(NotionImportRunRepository.class);
    private final Clock clock = Clock.fixed(
            CREATED_AT,
            ZoneOffset.UTC
    );
    private final NotionImportCommandService service = new NotionImportCommandService(
            workspaceRepository,
            workspaceMemberRepository,
            connectionRepository,
            importRunRepository,
            clock
    );

    @DisplayName("현재 OWNER가 CONNECTED Connection으로 요청하면 새 PENDING Run을 생성한다")
    @Test
    void start_success_createdPendingRun() {
        // given
        ContentSourceConnection connection = stubConnectedOwner();
        when(importRunRepository.findActiveByContentSourceConnectionId(CONNECTION_ID)).thenReturn(Optional.empty());
        NotionImportRun savedImportRun = mock(NotionImportRun.class);
        when(savedImportRun.getId()).thenReturn(IMPORT_RUN_ID);
        when(importRunRepository.save(any(NotionImportRun.class))).thenReturn(savedImportRun);
        ArgumentCaptor<NotionImportRun> importRunCaptor = ArgumentCaptor.forClass(NotionImportRun.class);

        // when
        NotionImportRunRequestResult result = service.start(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).isEqualTo(
                new NotionImportRunRequestResult(
                        IMPORT_RUN_ID,
                        true
                )
        );
        verify(connectionRepository).findByWorkspaceIdAndProviderForUpdate(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION
        );
        verify(importRunRepository).save(importRunCaptor.capture());
        NotionImportRun createdImportRun = importRunCaptor.getValue();
        assertThat(createdImportRun.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(createdImportRun.getContentSourceConnectionId()).isEqualTo(connection.getId());
        assertThat(createdImportRun.getRequestedByMemberId()).isEqualTo(MEMBER_ID);
        assertThat(createdImportRun.getStatus()).isEqualTo(NotionImportStatus.PENDING);
        assertThat(createdImportRun.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @DisplayName("같은 Connection에 활성 Run이 있으면 새 Row 없이 현재 Run을 반환한다")
    @Test
    void start_success_existingActiveRun() {
        // given
        stubConnectedOwner();
        NotionImportRun activeImportRun = mock(NotionImportRun.class);
        when(activeImportRun.getId()).thenReturn(IMPORT_RUN_ID);
        when(importRunRepository.findActiveByContentSourceConnectionId(CONNECTION_ID))
                .thenReturn(Optional.of(activeImportRun));

        // when
        NotionImportRunRequestResult result = service.start(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).isEqualTo(
                new NotionImportRunRequestResult(
                        IMPORT_RUN_ID,
                        false
                )
        );
        verify(
                importRunRepository,
                never()
        ).save(any(NotionImportRun.class));
    }

    @DisplayName("Workspace ID가 양수가 아니면 저장소를 조회하지 않는다")
    @Test
    void start_failure_invalidWorkspaceId() {
        // given
        ThrowingCallable action = () -> service.start(
                0L,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        verifyNoInteractions(
                workspaceRepository,
                workspaceMemberRepository,
                connectionRepository,
                importRunRepository
        );
    }

    @DisplayName("Workspace가 없으면 OWNER와 Connection을 조회하지 않는다")
    @Test
    void start_failure_workspaceNotFound() {
        // given
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.empty());
        ThrowingCallable action = () -> service.start(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        verifyNoInteractions(
                workspaceMemberRepository,
                connectionRepository,
                importRunRepository
        );
    }

    @DisplayName("현재 요청자가 OWNER가 아니면 Connection과 Run을 조회하지 않는다")
    @Test
    void start_failure_ownerRequired() {
        // given
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(mock(Workspace.class)));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(false);
        ThrowingCallable action = () -> service.start(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        verifyNoInteractions(
                connectionRepository,
                importRunRepository
        );
    }

    @DisplayName("Notion Connection이 없으면 활성 Run을 조회하지 않는다")
    @Test
    void start_failure_notionConnectionNotConnected() {
        // given
        stubCurrentOwner();
        when(
                connectionRepository.findByWorkspaceIdAndProviderForUpdate(
                        WORKSPACE_ID,
                        ContentSourceProvider.NOTION
                )
        ).thenReturn(Optional.empty());
        ThrowingCallable action = () -> service.start(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class)
                .extracting(exception -> ((NotionImportException) exception).getErrorCode())
                .isEqualTo(NotionImportErrorCode.NOTION_CONNECTION_NOT_CONNECTED);
        verifyNoInteractions(importRunRepository);
    }

    @DisplayName("Connection 승인자가 현재 OWNER가 아니면 재인증 오류로 시작을 거부한다")
    @Test
    void start_failure_notionConnectionReauthenticationRequired() {
        // given
        stubCurrentOwner();
        ContentSourceConnection connection = connection();
        when(
                connectionRepository.findByWorkspaceIdAndProviderForUpdate(
                        WORKSPACE_ID,
                        ContentSourceProvider.NOTION
                )
        ).thenReturn(Optional.of(connection));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        AUTHORIZING_MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(false);
        ThrowingCallable action = () -> service.start(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class)
                .extracting(exception -> ((NotionImportException) exception).getErrorCode())
                .isEqualTo(NotionImportErrorCode.NOTION_CONNECTION_REAUTHENTICATION_REQUIRED);
        verifyNoInteractions(importRunRepository);
    }

    private ContentSourceConnection stubConnectedOwner() {
        stubCurrentOwner();
        ContentSourceConnection connection = connection();
        when(
                connectionRepository.findByWorkspaceIdAndProviderForUpdate(
                        WORKSPACE_ID,
                        ContentSourceProvider.NOTION
                )
        ).thenReturn(Optional.of(connection));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        AUTHORIZING_MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(true);
        return connection;
    }

    private void stubCurrentOwner() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(mock(Workspace.class)));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(true);
    }

    private ContentSourceConnection connection() {
        ContentSourceConnection connection = mock(ContentSourceConnection.class);
        when(connection.getId()).thenReturn(CONNECTION_ID);
        when(connection.getWorkspaceId()).thenReturn(WORKSPACE_ID);
        when(connection.getAuthorizingMemberId()).thenReturn(AUTHORIZING_MEMBER_ID);
        return connection;
    }
}
