package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.global.exception.ErrorCode;
import com.knot.backend.global.exception.ProjectException;
import com.knot.backend.recording.application.RecordingStartService;
import com.knot.backend.recording.application.dto.command.RecordingStartCommand;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = AutowireMode.ALL)
class WorkspaceDeletionServiceIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant JOINED_AT = Instant.parse("2026-10-05T00:01:00Z");
    private static final String CONTROL_TOKEN = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefA";

    private final WorkspaceDeletionService workspaceDeletionService;
    private final WorkspaceLeaveService workspaceLeaveService;
    private final WorkspaceOwnershipTransferService workspaceOwnershipTransferService;
    private final WorkspaceInvitationService workspaceInvitationService;
    private final WorkspaceInvitationAcceptanceService workspaceInvitationAcceptanceService;
    private final RecordingStartService recordingStartService;
    private final TransactionTemplate transactionTemplate;
    private final JdbcClient jdbcClient;

    WorkspaceDeletionServiceIntegrationTest(
            WorkspaceDeletionService workspaceDeletionService,
            WorkspaceLeaveService workspaceLeaveService,
            WorkspaceOwnershipTransferService workspaceOwnershipTransferService,
            WorkspaceInvitationService workspaceInvitationService,
            WorkspaceInvitationAcceptanceService workspaceInvitationAcceptanceService,
            RecordingStartService recordingStartService,
            TransactionTemplate transactionTemplate,
            JdbcClient jdbcClient
    ) {
        this.workspaceDeletionService = workspaceDeletionService;
        this.workspaceLeaveService = workspaceLeaveService;
        this.workspaceOwnershipTransferService = workspaceOwnershipTransferService;
        this.workspaceInvitationService = workspaceInvitationService;
        this.workspaceInvitationAcceptanceService = workspaceInvitationAcceptanceService;
        this.recordingStartService = recordingStartService;
        this.transactionTemplate = transactionTemplate;
        this.jdbcClient = jdbcClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient.sql("""
                TRUNCATE TABLE recording_sessions, workspace_invitations, workspace_members, workspaces,
                    oauth_identities, members
                RESTART IDENTITY CASCADE
                """)
                .update();
    }

    @DisplayName("OWNER 삭제는 워크스페이스·활성 참여·진행 중 녹음을 같은 시각으로 함께 종료하고 다른 데이터는 보존한다")
    @Test
    void delete_success_endsWorkspaceMembershipsAndRecordingsTogether() {
        // given
        long ownerId = saveMember("owner");
        long memberId = saveMember("member");
        long formerId = saveMember("former");
        long otherId = saveMember("other");
        long workspaceId = saveWorkspace("삭제 팀");
        long otherWorkspaceId = saveWorkspace("다른 팀");
        long ownerMembershipId = saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        long memberMembershipId = saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );
        long formerMembershipId = saveWorkspaceMember(
                workspaceId,
                formerId,
                "MEMBER"
        );
        Instant formerLeftAt = JOINED_AT.plusSeconds(10);
        leaveMembership(
                formerMembershipId,
                formerLeftAt
        );
        long otherMembershipId = saveWorkspaceMember(
                otherWorkspaceId,
                memberId,
                "OWNER"
        );
        markLastViewed(memberMembershipId);
        long recordingId = saveRecording(
                workspaceId,
                memberId,
                "RECORDING"
        );
        long pausedRecordingId = saveRecording(
                workspaceId,
                ownerId,
                "PAUSED"
        );
        long endedRecordingId = saveRecording(
                workspaceId,
                ownerId,
                "ENDED"
        );
        long otherRecordingId = saveRecording(
                otherWorkspaceId,
                otherId,
                "RECORDING"
        );

        // when
        workspaceDeletionService.delete(
                ownerId,
                workspaceId
        );

        // then
        Instant deletedAt = workspaceDeletedAt(workspaceId);
        assertThat(deletedAt).isNotNull();
        assertThat(membershipLeftAt(ownerMembershipId)).isEqualTo(deletedAt);
        assertThat(membershipLeftAt(memberMembershipId)).isEqualTo(deletedAt);
        assertThat(lastViewedCount(memberId)).isZero();
        assertThat(membershipLeftAt(formerMembershipId)).isEqualTo(formerLeftAt);
        assertThat(recordingState(recordingId)).isEqualTo(
                new RecordingState(
                        "DISCARDED",
                        deletedAt
                )
        );
        assertThat(recordingState(pausedRecordingId).status()).isEqualTo("DISCARDED");
        assertThat(recordingState(endedRecordingId).status()).isEqualTo("ENDED");
        assertThat(membershipLeftAt(otherMembershipId)).isNull();
        assertThat(workspaceDeletedAt(otherWorkspaceId)).isNull();
        assertThat(recordingState(otherRecordingId).status()).isEqualTo("RECORDING");
    }

    @DisplayName("이미 삭제된 워크스페이스를 다시 삭제하면 404로 거절하고 기존 삭제 시각을 바꾸지 않는다")
    @Test
    void delete_failure_alreadyDeleted() {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace("반복 삭제 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        workspaceDeletionService.delete(
                ownerId,
                workspaceId
        );
        Instant deletedAt = workspaceDeletedAt(workspaceId);

        // when
        Throwable thrown = catchThrowable(
                () -> workspaceDeletionService.delete(
                        ownerId,
                        workspaceId
                )
        );

        // then
        assertThat(errorCode(thrown)).isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        assertThat(workspaceDeletedAt(workspaceId)).isEqualTo(deletedAt);
    }

    @DisplayName("삭제 후 같은 트랜잭션에서 예외가 나면 워크스페이스 삭제·참여 종료·녹음 폐기를 함께 롤백한다")
    @Test
    void delete_failure_rollsBackWhenOuterTransactionFails() {
        // given
        long ownerId = saveMember("owner");
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("롤백 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );
        long recordingId = saveRecording(
                workspaceId,
                memberId,
                "RECORDING"
        );

        // when
        Throwable thrown = catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
            workspaceDeletionService.delete(
                    ownerId,
                    workspaceId
            );
            throw new IllegalStateException("삭제 후 롤백 검증");
        }));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(workspaceDeletedAt(workspaceId)).isNull();
        assertThat(activeMembershipCount(workspaceId)).isEqualTo(2);
        assertThat(recordingState(recordingId)).isEqualTo(
                new RecordingState(
                        "RECORDING",
                        null
                )
        );
    }

    @DisplayName("삭제와 초대 수락이 경합하면 수락 후 함께 종료되거나 삭제 후 수락이 404로 거절된다")
    @Test
    void delete_success_whenRacingWithInvitationAccept() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long joiningId = saveMember("joining");
        long workspaceId = saveWorkspace("삭제 수락 경합 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        WorkspaceInvitationResult invitation = workspaceInvitationService.issue(
                workspaceId,
                ownerId
        );

        // when
        RaceResult result = race(
                () -> workspaceDeletionService.delete(
                        ownerId,
                        workspaceId
                ),
                () -> workspaceInvitationAcceptanceService.accept(
                        invitation.code(),
                        "delete-accept-race",
                        joiningId
                )
        );

        // then
        assertThat(result.deleteErrorCode()).isNull();
        assertThat(result.otherErrorCode()).isIn(
                null,
                WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND
        );
        assertThat(workspaceDeletedAt(workspaceId)).isNotNull();
        assertThat(activeMembershipCount(workspaceId)).isZero();
    }

    @DisplayName("삭제와 OWNER 승계가 경합하면 한쪽만 성공하고 활성 OWNER 없는 워크스페이스를 남기지 않는다")
    @Test
    void delete_successOrRejected_whenRacingWithOwnershipTransfer() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long successorId = saveMember("successor");
        long workspaceId = saveWorkspace("삭제 승계 경합 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                successorId,
                "MEMBER"
        );

        // when
        RaceResult result = race(
                () -> workspaceDeletionService.delete(
                        ownerId,
                        workspaceId
                ),
                () -> workspaceOwnershipTransferService.transferOwnership(
                        ownerId,
                        workspaceId,
                        successorId
                )
        );

        // then
        boolean deletedFirst = result.deleteErrorCode() == null
                && result.otherErrorCode() == WorkspaceErrorCode.WORKSPACE_NOT_FOUND;
        boolean transferredFirst = result.otherErrorCode() == null
                && result.deleteErrorCode() == WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED;
        assertThat(deletedFirst || transferredFirst).isTrue();
        if (deletedFirst) {
            assertThat(activeMembershipCount(workspaceId)).isZero();
        } else {
            assertThat(workspaceDeletedAt(workspaceId)).isNull();
            assertThat(activeOwnerCount(workspaceId)).isEqualTo(1);
        }
    }

    @DisplayName("삭제와 MEMBER 탈퇴가 경합해도 워크스페이스는 삭제되고 활성 참여가 남지 않는다")
    @Test
    void delete_success_whenRacingWithMemberLeave() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("삭제 탈퇴 경합 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );

        // when
        RaceResult result = race(
                () -> workspaceDeletionService.delete(
                        ownerId,
                        workspaceId
                ),
                () -> workspaceLeaveService.leave(
                        memberId,
                        workspaceId
                )
        );

        // then
        assertThat(result.deleteErrorCode()).isNull();
        assertThat(result.otherErrorCode()).isIn(
                null,
                WorkspaceErrorCode.WORKSPACE_NOT_FOUND
        );
        assertThat(workspaceDeletedAt(workspaceId)).isNotNull();
        assertThat(activeMembershipCount(workspaceId)).isZero();
    }

    @DisplayName("삭제와 녹음 시작이 경합하면 시작된 녹음은 폐기되거나 삭제 후 시작이 404로 거절된다")
    @Test
    void delete_success_whenRacingWithRecordingStart() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("삭제 녹음 경합 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );

        // when
        RaceResult result = race(
                () -> workspaceDeletionService.delete(
                        ownerId,
                        workspaceId
                ),
                () -> recordingStartService.start(
                        workspaceId,
                        memberId,
                        new RecordingStartCommand(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                CONTROL_TOKEN
                        )
                )
        );

        // then
        assertThat(result.deleteErrorCode()).isNull();
        assertThat(result.otherErrorCode()).isIn(
                null,
                WorkspaceErrorCode.WORKSPACE_NOT_FOUND
        );
        assertThat(activeRecordingCount(workspaceId)).isZero();
        if (result.otherErrorCode() == null) {
            assertThat(
                    recordingCountByStatus(
                            workspaceId,
                            "DISCARDED"
                    )
            ).isEqualTo(1);
        }
    }

    private RaceResult race(
            ThrowingOperation delete,
            ThrowingOperation other
    ) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<ErrorCode> deleteResult = executorService.submit(
                    outcomeAfterBarrier(
                            barrier,
                            delete
                    )
            );
            Future<ErrorCode> otherResult = executorService.submit(
                    outcomeAfterBarrier(
                            barrier,
                            other
                    )
            );
            return new RaceResult(
                    deleteResult.get(
                            10,
                            TimeUnit.SECONDS
                    ),
                    otherResult.get(
                            10,
                            TimeUnit.SECONDS
                    )
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    private Callable<ErrorCode> outcomeAfterBarrier(
            CyclicBarrier barrier,
            ThrowingOperation operation
    ) {
        return () -> {
            barrier.await(
                    5,
                    TimeUnit.SECONDS
            );
            try {
                operation.run();
                return null;
            } catch (ProjectException exception) {
                return exception.getErrorCode();
            }
        };
    }

    private ErrorCode errorCode(Throwable thrown) {
        assertThat(thrown).isInstanceOf(WorkspaceException.class);
        return ((WorkspaceException) thrown).getErrorCode();
    }

    private long saveMember(String nickname) {
        return jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES (:nickname, NULL)
                RETURNING id
                """)
                .param(
                        "nickname",
                        nickname
                )
                .query(Long.class)
                .single();
    }

    private long saveWorkspace(String name) {
        return jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES (:name, CAST(:createdAt AS TIMESTAMPTZ))
                RETURNING id
                """)
                .param(
                        "name",
                        name
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private long saveWorkspaceMember(
            long workspaceId,
            long memberId,
            String role
    ) {
        return jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, :role, CAST(:joinedAt AS TIMESTAMPTZ))
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "role",
                        role
                )
                .param(
                        "joinedAt",
                        JOINED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private long saveRecording(
            long workspaceId,
            long memberId,
            String status
    ) {
        return jdbcClient.sql("""
                INSERT INTO recording_sessions (
                    workspace_id, member_id, request_id, tab_id, control_token_hash, status,
                    started_at, current_interval_started_at, ended_at, last_seen_at
                )
                VALUES (
                    :workspaceId, :memberId, gen_random_uuid(), gen_random_uuid(), repeat('a', 64), :status,
                    CAST(:startedAt AS TIMESTAMPTZ),
                    CASE WHEN :status = 'RECORDING' THEN CAST(:startedAt AS TIMESTAMPTZ) END,
                    CASE WHEN :status = 'ENDED' THEN CAST(:startedAt AS TIMESTAMPTZ) END,
                    CAST(:startedAt AS TIMESTAMPTZ)
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "status",
                        status
                )
                .param(
                        "startedAt",
                        JOINED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private void markLastViewed(long membershipId) {
        jdbcClient.sql("""
                UPDATE workspace_members
                SET last_viewed = TRUE
                WHERE id = :membershipId
                """)
                .param(
                        "membershipId",
                        membershipId
                )
                .update();
    }

    private void leaveMembership(
            long membershipId,
            Instant leftAt
    ) {
        jdbcClient.sql("""
                UPDATE workspace_members
                SET left_at = CAST(:leftAt AS TIMESTAMPTZ),
                    last_viewed = FALSE
                WHERE id = :membershipId
                """)
                .param(
                        "membershipId",
                        membershipId
                )
                .param(
                        "leftAt",
                        leftAt.toString()
                )
                .update();
    }

    private RecordingState recordingState(long recordingId) {
        return jdbcClient.sql("SELECT status, ended_at FROM recording_sessions WHERE id = :recordingId")
                .param(
                        "recordingId",
                        recordingId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) -> new RecordingState(
                                resultSet.getString("status"),
                                toInstant(
                                        resultSet.getObject(
                                                "ended_at",
                                                OffsetDateTime.class
                                        )
                                )
                        )
                )
                .single();
    }

    private Instant membershipLeftAt(long membershipId) {
        return jdbcClient.sql("SELECT left_at FROM workspace_members WHERE id = :membershipId")
                .param(
                        "membershipId",
                        membershipId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) -> Optional.ofNullable(
                                toInstant(
                                        resultSet.getObject(
                                                "left_at",
                                                OffsetDateTime.class
                                        )
                                )
                        )
                )
                .single()
                .orElse(null);
    }

    private Instant workspaceDeletedAt(long workspaceId) {
        return jdbcClient.sql("SELECT deleted_at FROM workspaces WHERE id = :workspaceId")
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) -> Optional.ofNullable(
                                toInstant(
                                        resultSet.getObject(
                                                "deleted_at",
                                                OffsetDateTime.class
                                        )
                                )
                        )
                )
                .single()
                .orElse(null);
    }

    private long activeMembershipCount(long workspaceId) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM workspace_members
                WHERE workspace_id = :workspaceId
                  AND left_at IS NULL
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
    }

    private long activeOwnerCount(long workspaceId) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM workspace_members
                WHERE workspace_id = :workspaceId
                  AND role = 'OWNER'
                  AND left_at IS NULL
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
    }

    private long lastViewedCount(long memberId) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM workspace_members
                WHERE member_id = :memberId
                  AND last_viewed
                """)
                .param(
                        "memberId",
                        memberId
                )
                .query(Long.class)
                .single();
    }

    private long activeRecordingCount(long workspaceId) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM recording_sessions
                WHERE workspace_id = :workspaceId
                  AND status IN ('RECORDING', 'PAUSED')
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
    }

    private long recordingCountByStatus(
            long workspaceId,
            String status
    ) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM recording_sessions
                WHERE workspace_id = :workspaceId
                  AND status = :status
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "status",
                        status
                )
                .query(Long.class)
                .single();
    }

    private Instant toInstant(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toInstant();
    }

    private interface ThrowingOperation {

        void run();
    }

    private record RecordingState(
            String status,
            Instant endedAt
    ) {
    }

    private record RaceResult(
            ErrorCode deleteErrorCode,
            ErrorCode otherErrorCode
    ) {
    }
}
