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

import com.knot.backend.workspace.application.dto.result.ContentImportRunRequestResult;
import com.knot.backend.workspace.domain.ContentImportErrorCode;
import com.knot.backend.workspace.domain.ContentImportException;
import com.knot.backend.workspace.domain.ContentImportRun;
import com.knot.backend.workspace.domain.ContentImportRunRepository;
import com.knot.backend.workspace.domain.ContentImportStatus;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
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

class ContentImportRetryCommandServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final Long OTHER_WORKSPACE_ID = 7L;
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
    private final ContentImportRunRepository importRunRepository = mock(ContentImportRunRepository.class);
    private final Clock clock = Clock.fixed(
            CREATED_AT,
            ZoneOffset.UTC
    );
    private final ContentImportCommandService service = new ContentImportCommandService(
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
        ContentImportRun originalImportRun = stubRetryableOriginalRun();
        ContentSourceConnection connection = stubOriginalConnectionOwner();
        when(importRunRepository.findActiveByContentSourceConnectionId(CONNECTION_ID)).thenReturn(Optional.empty());
        ContentImportRun savedImportRun = mock(ContentImportRun.class);
        when(savedImportRun.getId()).thenReturn(NEW_IMPORT_RUN_ID);
        when(importRunRepository.save(any(ContentImportRun.class))).thenReturn(savedImportRun);
        ArgumentCaptor<ContentImportRun> importRunCaptor = ArgumentCaptor.forClass(ContentImportRun.class);
        String originalSnapshot = snapshot(originalImportRun);

        // when
        ContentImportRunRequestResult result = service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).isEqualTo(
                new ContentImportRunRequestResult(
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
                .findByIdForUpdate(CONNECTION_ID);
        verify(importRunRepository).save(importRunCaptor.capture());
        ContentImportRun createdImportRun = importRunCaptor.getValue();
        assertThat(createdImportRun.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(createdImportRun.getContentSourceConnectionId()).isEqualTo(connection.getId());
        assertThat(createdImportRun.getRequestedByMemberId()).isEqualTo(MEMBER_ID);
        assertThat(createdImportRun.getStatus()).isEqualTo(ContentImportStatus.PENDING);
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
        ContentImportRun originalImportRun = stubRetryableOriginalRun();
        stubOriginalConnectionOwner();
        ContentImportRun activeImportRun = mock(ContentImportRun.class);
        when(activeImportRun.getId()).thenReturn(NEW_IMPORT_RUN_ID);
        when(importRunRepository.findActiveByContentSourceConnectionId(CONNECTION_ID))
                .thenReturn(Optional.of(activeImportRun));
        String originalSnapshot = snapshot(originalImportRun);

        // when
        ContentImportRunRequestResult result = service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).isEqualTo(
                new ContentImportRunRequestResult(
                        NEW_IMPORT_RUN_ID,
                        false
                )
        );
        verify(
                importRunRepository,
                never()
        ).save(any(ContentImportRun.class));
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
        assertThat(thrown).isInstanceOf(ContentImportException.class)
                .extracting(exception -> ((ContentImportException) exception).contentImportErrorCode())
                .isEqualTo(ContentImportErrorCode.INVALID_CONTENT_IMPORT_RUN_ID);
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
        assertThat(thrown).isInstanceOf(ContentImportException.class)
                .extracting(exception -> ((ContentImportException) exception).contentImportErrorCode())
                .isEqualTo(ContentImportErrorCode.CONTENT_IMPORT_RUN_NOT_FOUND);
        verifyNoInteractions(
                workspaceRepository,
                workspaceMemberRepository,
                connectionRepository
        );
        verify(
                importRunRepository,
                never()
        ).save(any(ContentImportRun.class));
    }

    @DisplayName("현재 MEMBER는 원본 Run을 볼 수 있어도 재시도할 수 없다")
    @Test
    void retry_failure_ownerRequired() {
        // given
        ContentImportRun originalImportRun = failedImportRun();
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
    @EnumSource(value = ContentImportStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    @ParameterizedTest(name = "{0}")
    void retry_failure_originalImportRunNotRetryable(ContentImportStatus status) {
        // given
        ContentImportRun originalImportRun = importRun(status);
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
        assertThat(thrown).isInstanceOf(ContentImportException.class)
                .extracting(exception -> ((ContentImportException) exception).contentImportErrorCode())
                .isEqualTo(ContentImportErrorCode.CONTENT_IMPORT_NOT_RETRYABLE);
        verifyNoInteractions(
                workspaceRepository,
                connectionRepository
        );
        verify(
                importRunRepository,
                never()
        ).findActiveByContentSourceConnectionId(any());
    }

    @DisplayName("원본 Run의 Content Source Connection이 없으면 원본을 보존하고 재시도를 거부한다")
    @Test
    void retry_failure_contentSourceConnectionNotConnected() {
        // given
        ContentImportRun originalImportRun = stubRetryableOriginalRun();
        when(connectionRepository.findByIdForUpdate(CONNECTION_ID)).thenReturn(Optional.empty());
        String originalSnapshot = snapshot(originalImportRun);
        ThrowingCallable action = () -> service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class)
                .extracting(exception -> ((ContentImportException) exception).contentImportErrorCode())
                .isEqualTo(ContentImportErrorCode.CONTENT_SOURCE_CONNECTION_NOT_CONNECTED);
        assertThat(snapshot(originalImportRun)).isEqualTo(originalSnapshot);
        verify(
                importRunRepository,
                never()
        ).findActiveByContentSourceConnectionId(any());
    }

    @DisplayName("원본 Run과 Connection의 Workspace가 다르면 잘못된 Run으로 처리한다")
    @Test
    void retry_failure_contentSourceConnectionWorkspaceMismatch() {
        // given
        ContentImportRun originalImportRun = stubRetryableOriginalRun();
        ContentSourceConnection connection = connection(OTHER_WORKSPACE_ID);
        when(connectionRepository.findByIdForUpdate(CONNECTION_ID)).thenReturn(Optional.of(connection));
        String originalSnapshot = snapshot(originalImportRun);
        ThrowingCallable action = () -> service.retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ContentImportException.class)
                .extracting(exception -> ((ContentImportException) exception).contentImportErrorCode())
                .isEqualTo(ContentImportErrorCode.INVALID_CONTENT_IMPORT_RUN);
        assertThat(snapshot(originalImportRun)).isEqualTo(originalSnapshot);
        verify(
                importRunRepository,
                never()
        ).findActiveByContentSourceConnectionId(any());
    }

    @DisplayName("Connection 승인자가 현재 OWNER가 아니면 FAILED 원본을 보존하고 재인증 오류를 반환한다")
    @Test
    void retry_failure_contentSourceConnectionReauthenticationRequired() {
        // given
        ContentImportRun originalImportRun = stubRetryableOriginalRun();
        ContentSourceConnection connection = connection();
        when(connectionRepository.findByIdForUpdate(CONNECTION_ID)).thenReturn(Optional.of(connection));
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
        assertThat(thrown).isInstanceOf(ContentImportException.class)
                .extracting(exception -> ((ContentImportException) exception).contentImportErrorCode())
                .isEqualTo(ContentImportErrorCode.CONTENT_SOURCE_CONNECTION_REAUTHENTICATION_REQUIRED);
        assertThat(snapshot(originalImportRun)).isEqualTo(originalSnapshot);
        verify(
                importRunRepository,
                never()
        ).findActiveByContentSourceConnectionId(any());
    }

    private ContentImportRun stubRetryableOriginalRun() {
        ContentImportRun originalImportRun = failedImportRun();
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

    private ContentSourceConnection stubOriginalConnectionOwner() {
        ContentSourceConnection connection = connection();
        when(connectionRepository.findByIdForUpdate(CONNECTION_ID)).thenReturn(Optional.of(connection));
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
        return connection(WORKSPACE_ID);
    }

    private ContentSourceConnection connection(Long workspaceId) {
        ContentSourceConnection connection = mock(ContentSourceConnection.class);
        when(connection.getId()).thenReturn(CONNECTION_ID);
        when(connection.getWorkspaceId()).thenReturn(workspaceId);
        when(connection.getAuthorizingMemberId()).thenReturn(AUTHORIZING_MEMBER_ID);
        return connection;
    }

    private ContentImportRun failedImportRun() {
        return importRun(ContentImportStatus.FAILED);
    }

    private ContentImportRun importRun(ContentImportStatus status) {
        Instant startedAt = switch (status) {
            case PENDING -> null;
            case RUNNING, COMPLETED, FAILED -> CREATED_AT.minusSeconds(2);
        };
        Instant completedAt = switch (status) {
            case PENDING, RUNNING -> null;
            case COMPLETED, FAILED -> CREATED_AT.minusSeconds(1);
        };
        int processedPageCount = switch (status) {
            case PENDING -> 0;
            case RUNNING, FAILED -> 4;
            case COMPLETED -> 10;
        };
        return ContentImportRun.create(
                WORKSPACE_ID,
                CONNECTION_ID,
                AUTHORIZING_MEMBER_ID,
                status,
                10,
                processedPageCount,
                startedAt,
                completedAt,
                CREATED_AT.minusSeconds(3)
        );
    }

    private String snapshot(ContentImportRun importRun) {
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
