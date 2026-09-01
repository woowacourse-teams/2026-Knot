package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class NotionImportRetryCommandServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final long AUTHORIZING_MEMBER_ID = 3L;
    private static final Long CONNECTION_ID = 4L;
    private static final Long ORIGINAL_IMPORT_RUN_ID = 5L;
    private static final Long NEW_IMPORT_RUN_ID = 6L;
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

    @DisplayName("현재 OWNER가 FAILED Run을 재시도하면 원본을 보존하고 새 PENDING Run을 생성한다")
    @Test
    void retry_success_createdPendingRun() {
        // given
        NotionImportRun originalImportRun = stubRetryableOriginalRun();
        ContentSourceConnection connection = stubConnectedOwner();
        when(importRunRepository.findActiveByContentSourceConnectionId(CONNECTION_ID)).thenReturn(Optional.empty());
        NotionImportRun savedImportRun = mock(NotionImportRun.class);
        when(savedImportRun.getId()).thenReturn(NEW_IMPORT_RUN_ID);
        when(importRunRepository.save(any(NotionImportRun.class))).thenReturn(savedImportRun);
        ArgumentCaptor<NotionImportRun> importRunCaptor = ArgumentCaptor.forClass(NotionImportRun.class);
        String originalSnapshot = snapshot(originalImportRun);

        // when
        NotionImportRunRequestResult result = service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).isEqualTo(
                new NotionImportRunRequestResult(
                        NEW_IMPORT_RUN_ID,
                        true
                )
        );
        InOrder validationOrder = inOrder(
                importRunRepository,
                workspaceMemberRepository,
                connectionRepository
        );
        validationOrder.verify(importRunRepository)
                .findVisibleByIdAndMemberId(
                        ORIGINAL_IMPORT_RUN_ID,
                        MEMBER_ID
                );
        validationOrder.verify(workspaceMemberRepository)
                .existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                );
        validationOrder.verify(connectionRepository)
                .findByWorkspaceIdAndProviderForUpdate(
                        WORKSPACE_ID,
                        ContentSourceProvider.NOTION
                );
        verify(importRunRepository).save(importRunCaptor.capture());
        NotionImportRun createdImportRun = importRunCaptor.getValue();
        assertThat(createdImportRun.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(createdImportRun.getContentSourceConnectionId()).isEqualTo(connection.getId());
        assertThat(createdImportRun.getRequestedByMemberId()).isEqualTo(MEMBER_ID);
        assertThat(createdImportRun.getStatus()).isEqualTo(NotionImportStatus.PENDING);
        assertThat(createdImportRun.getTotalPageCount()).isNull();
        assertThat(createdImportRun.getProcessedPageCount()).isZero();
        assertThat(createdImportRun.getStartedAt()).isNull();
        assertThat(createdImportRun.getCompletedAt()).isNull();
        assertThat(createdImportRun.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(snapshot(originalImportRun)).isEqualTo(originalSnapshot);
        verifyNoInteractions(workspaceRepository);
    }

    @DisplayName("같은 Workspace에 활성 Run이 있으면 원본을 보존하고 현재 활성 Run을 반환한다")
    @Test
    void retry_success_existingActiveRun() {
        // given
        NotionImportRun originalImportRun = stubRetryableOriginalRun();
        stubConnectedOwner();
        NotionImportRun activeImportRun = mock(NotionImportRun.class);
        when(activeImportRun.getId()).thenReturn(NEW_IMPORT_RUN_ID);
        when(importRunRepository.findActiveByContentSourceConnectionId(CONNECTION_ID))
                .thenReturn(Optional.of(activeImportRun));
        String originalSnapshot = snapshot(originalImportRun);

        // when
        NotionImportRunRequestResult result = service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).isEqualTo(
                new NotionImportRunRequestResult(
                        NEW_IMPORT_RUN_ID,
                        false
                )
        );
        verify(
                importRunRepository,
                never()
        ).save(any(NotionImportRun.class));
        assertThat(snapshot(originalImportRun)).isEqualTo(originalSnapshot);
    }

    @DisplayName("Import Run ID가 양수가 아니면 저장소를 조회하지 않는다")
    @Test
    void retry_failure_invalidImportRunId() {
        // given
        ThrowingCallable action = () -> service.retry(
                0L,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class)
                .extracting(exception -> ((NotionImportException) exception).getErrorCode())
                .isEqualTo(NotionImportErrorCode.INVALID_NOTION_IMPORT_RUN_ID);
        verifyNoInteractions(
                workspaceRepository,
                workspaceMemberRepository,
                connectionRepository,
                importRunRepository
        );
    }

    @DisplayName("현재 멤버에게 보이지 않는 원본 Run은 미존재와 같은 오류로 처리한다")
    @Test
    void retry_failure_originalImportRunNotFound() {
        // given
        when(
                importRunRepository.findVisibleByIdAndMemberId(
                        ORIGINAL_IMPORT_RUN_ID,
                        MEMBER_ID
                )
        ).thenReturn(Optional.empty());
        ThrowingCallable action = () -> service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class)
                .extracting(exception -> ((NotionImportException) exception).getErrorCode())
                .isEqualTo(NotionImportErrorCode.NOTION_IMPORT_RUN_NOT_FOUND);
        verifyNoInteractions(
                workspaceRepository,
                workspaceMemberRepository,
                connectionRepository
        );
        verify(
                importRunRepository,
                never()
        ).save(any(NotionImportRun.class));
    }

    @DisplayName("현재 MEMBER는 원본 Run을 볼 수 있어도 재시도할 수 없다")
    @Test
    void retry_failure_ownerRequired() {
        // given
        NotionImportRun originalImportRun = failedImportRun();
        when(
                importRunRepository.findVisibleByIdAndMemberId(
                        ORIGINAL_IMPORT_RUN_ID,
                        MEMBER_ID
                )
        ).thenReturn(Optional.of(originalImportRun));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(false);
        ThrowingCallable action = () -> service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        verifyNoInteractions(
                workspaceRepository,
                connectionRepository
        );
        verify(
                importRunRepository,
                never()
        ).findActiveByContentSourceConnectionId(any());
    }

    @DisplayName("FAILED가 아닌 원본 Run은 Connection을 조회하기 전에 재시도를 거부한다")
    @EnumSource(value = NotionImportStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    @ParameterizedTest(name = "{0}")
    void retry_failure_originalImportRunNotRetryable(NotionImportStatus status) {
        // given
        NotionImportRun originalImportRun = importRun(status);
        when(
                importRunRepository.findVisibleByIdAndMemberId(
                        ORIGINAL_IMPORT_RUN_ID,
                        MEMBER_ID
                )
        ).thenReturn(Optional.of(originalImportRun));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(true);
        ThrowingCallable action = () -> service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class)
                .extracting(exception -> ((NotionImportException) exception).getErrorCode())
                .isEqualTo(NotionImportErrorCode.NOTION_IMPORT_NOT_RETRYABLE);
        verifyNoInteractions(
                workspaceRepository,
                connectionRepository
        );
        verify(
                importRunRepository,
                never()
        ).findActiveByContentSourceConnectionId(any());
    }

    @DisplayName("Notion Connection이 없으면 FAILED 원본을 보존하고 재시도를 거부한다")
    @Test
    void retry_failure_notionConnectionNotConnected() {
        // given
        NotionImportRun originalImportRun = stubRetryableOriginalRun();
        when(
                connectionRepository.findByWorkspaceIdAndProviderForUpdate(
                        WORKSPACE_ID,
                        ContentSourceProvider.NOTION
                )
        ).thenReturn(Optional.empty());
        String originalSnapshot = snapshot(originalImportRun);
        ThrowingCallable action = () -> service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class)
                .extracting(exception -> ((NotionImportException) exception).getErrorCode())
                .isEqualTo(NotionImportErrorCode.NOTION_CONNECTION_NOT_CONNECTED);
        assertThat(snapshot(originalImportRun)).isEqualTo(originalSnapshot);
        verify(
                importRunRepository,
                never()
        ).findActiveByContentSourceConnectionId(any());
    }

    @DisplayName("Connection 승인자가 현재 OWNER가 아니면 FAILED 원본을 보존하고 재인증 오류를 반환한다")
    @Test
    void retry_failure_notionConnectionReauthenticationRequired() {
        // given
        NotionImportRun originalImportRun = stubRetryableOriginalRun();
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
        String originalSnapshot = snapshot(originalImportRun);
        ThrowingCallable action = () -> service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionImportException.class)
                .extracting(exception -> ((NotionImportException) exception).getErrorCode())
                .isEqualTo(NotionImportErrorCode.NOTION_CONNECTION_REAUTHENTICATION_REQUIRED);
        assertThat(snapshot(originalImportRun)).isEqualTo(originalSnapshot);
        verify(
                importRunRepository,
                never()
        ).findActiveByContentSourceConnectionId(any());
    }

    private NotionImportRun stubRetryableOriginalRun() {
        NotionImportRun originalImportRun = failedImportRun();
        when(
                importRunRepository.findVisibleByIdAndMemberId(
                        ORIGINAL_IMPORT_RUN_ID,
                        MEMBER_ID
                )
        ).thenReturn(Optional.of(originalImportRun));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberIdAndRole(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        WorkspaceMemberRole.OWNER
                )
        ).thenReturn(true);
        return originalImportRun;
    }

    private ContentSourceConnection stubConnectedOwner() {
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

    private ContentSourceConnection connection() {
        ContentSourceConnection connection = mock(ContentSourceConnection.class);
        when(connection.getId()).thenReturn(CONNECTION_ID);
        when(connection.getWorkspaceId()).thenReturn(WORKSPACE_ID);
        when(connection.getAuthorizingMemberId()).thenReturn(AUTHORIZING_MEMBER_ID);
        return connection;
    }

    private NotionImportRun failedImportRun() {
        return importRun(NotionImportStatus.FAILED);
    }

    private NotionImportRun importRun(NotionImportStatus status) {
        Instant startedAt = switch (status) {
            case PENDING -> null;
            case RUNNING, COMPLETED, FAILED -> CREATED_AT.minusSeconds(2);
        };
        Instant completedAt = switch (status) {
            case PENDING, RUNNING -> null;
            case COMPLETED, FAILED -> CREATED_AT.minusSeconds(1);
        };
        return NotionImportRun.create(
                WORKSPACE_ID,
                CONNECTION_ID,
                AUTHORIZING_MEMBER_ID,
                status,
                10,
                4,
                startedAt,
                completedAt,
                CREATED_AT.minusSeconds(3)
        );
    }

    private String snapshot(NotionImportRun importRun) {
        return "%s|%s|%s|%s|%s|%s|%s|%s".formatted(
                importRun.getWorkspaceId(),
                importRun.getContentSourceConnectionId(),
                importRun.getRequestedByMemberId(),
                importRun.getStatus(),
                importRun.getTotalPageCount(),
                importRun.getProcessedPageCount(),
                importRun.getStartedAt(),
                importRun.getCompletedAt()
        );
    }
}
