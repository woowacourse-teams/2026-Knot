package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationAcceptanceResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class WorkspaceLeaveServiceIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");
    private static final Instant JOINED_AT = Instant.parse("2026-08-31T00:01:00Z");

    private final WorkspaceLeaveService workspaceLeaveService;
    private final WorkspaceLastViewedService workspaceLastViewedService;
    private final WorkspaceInvitationService workspaceInvitationService;
    private final WorkspaceInvitationAcceptanceService workspaceInvitationAcceptanceService;
    private final TransactionTemplate transactionTemplate;
    private final JdbcClient jdbcClient;

    WorkspaceLeaveServiceIntegrationTest(
            WorkspaceLeaveService workspaceLeaveService,
            WorkspaceLastViewedService workspaceLastViewedService,
            WorkspaceInvitationService workspaceInvitationService,
            WorkspaceInvitationAcceptanceService workspaceInvitationAcceptanceService,
            TransactionTemplate transactionTemplate,
            JdbcClient jdbcClient
    ) {
        this.workspaceLeaveService = workspaceLeaveService;
        this.workspaceLastViewedService = workspaceLastViewedService;
        this.workspaceInvitationService = workspaceInvitationService;
        this.workspaceInvitationAcceptanceService = workspaceInvitationAcceptanceService;
        this.transactionTemplate = transactionTemplate;
        this.jdbcClient = jdbcClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient.sql("""
                TRUNCATE TABLE workspace_invitations, workspace_members, workspaces, oauth_identities, members
                RESTART IDENTITY CASCADE
                """)
                .update();
    }

    @DisplayName("활성 MEMBER가 탈퇴하면 이력을 남기고 마지막 조회 상태를 해제한다")
    @Test
    void leave_success_activeMember() {
        // given
        long ownerMemberId = saveMember("owner");
        long leavingMemberId = saveMember("leaving");
        long workspaceId = saveWorkspace("탈퇴 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerMemberId,
                "OWNER"
        );
        long leavingMembershipId = saveWorkspaceMember(
                workspaceId,
                leavingMemberId,
                "MEMBER"
        );
        markLastViewed(leavingMembershipId);

        // when
        workspaceLeaveService.leave(
                leavingMemberId,
                workspaceId
        );

        // then
        MembershipState membership = membershipState(leavingMembershipId);
        assertThat(membership.leftAt()).isNotNull()
                .isAfterOrEqualTo(JOINED_AT);
        assertThat(membership.lastViewed()).isFalse();
        assertThat(workspaceDeletedAt(workspaceId)).isNull();
        assertThat(activeMembershipCount(workspaceId)).isEqualTo(1);
    }

    @DisplayName("이미 탈퇴한 사용자가 다시 탈퇴하면 기존 탈퇴 이력을 변경하지 않는다")
    @Test
    void leave_success_alreadyLeftMembership() {
        // given
        long memberId = saveMember("retry");
        long workspaceId = saveWorkspace("재시도 팀");
        long membershipId = saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );
        Instant originalLeftAt = JOINED_AT.plusSeconds(10);
        leaveMembership(
                membershipId,
                originalLeftAt
        );

        // when
        workspaceLeaveService.leave(
                memberId,
                workspaceId
        );

        // then
        assertThat(membershipState(membershipId).leftAt()).isEqualTo(originalLeftAt);
        assertThat(workspaceDeletedAt(workspaceId)).isNull();
    }

    @DisplayName("다른 활성 멤버가 있는 OWNER 탈퇴는 거절하고 상태를 롤백한다")
    @Test
    void leave_failure_ownerTransferRequiredRollsBack() {
        // given
        long ownerMemberId = saveMember("owner");
        long otherMemberId = saveMember("member");
        long workspaceId = saveWorkspace("OWNER 거절 팀");
        long ownerMembershipId = saveWorkspaceMember(
                workspaceId,
                ownerMemberId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                otherMemberId,
                "MEMBER"
        );

        // when
        Throwable thrown = catchThrowable(
                () -> workspaceLeaveService.leave(
                        ownerMemberId,
                        workspaceId
                )
        );

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_TRANSFER_REQUIRED);
        assertThat(membershipState(ownerMembershipId).leftAt()).isNull();
        assertThat(workspaceDeletedAt(workspaceId)).isNull();
        assertThat(activeMembershipCount(workspaceId)).isEqualTo(2);
    }

    @DisplayName("마지막 활성 멤버가 탈퇴하면 멤버십과 워크스페이스를 같은 시각으로 논리 삭제한다")
    @Test
    void leave_success_lastActiveMemberDeletesWorkspace() {
        // given
        long memberId = saveMember("last");
        long workspaceId = saveWorkspace("마지막 팀");
        long membershipId = saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );

        // when
        workspaceLeaveService.leave(
                memberId,
                workspaceId
        );

        // then
        MembershipState membership = membershipState(membershipId);
        assertThat(membership.leftAt()).isNotNull();
        assertThat(workspaceDeletedAt(workspaceId)).isEqualTo(membership.leftAt());
        assertThat(activeMembershipCount(workspaceId)).isZero();
    }

    @DisplayName("삭제된 워크스페이스 탈퇴는 404로 거절하고 멤버십 이력을 변경하지 않는다")
    @Test
    void leave_failure_deletedWorkspaceNotFound() {
        // given
        long memberId = saveMember("deleted");
        long workspaceId = saveWorkspace("삭제된 팀");
        long membershipId = saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );
        deleteWorkspace(
                workspaceId,
                CREATED_AT.plusSeconds(60)
        );

        // when
        Throwable thrown = catchThrowable(
                () -> workspaceLeaveService.leave(
                        memberId,
                        workspaceId
                )
        );

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        assertThat(membershipState(membershipId).leftAt()).isNull();
    }

    @DisplayName("탈퇴와 마지막 조회 갱신이 경합해도 탈퇴한 멤버십은 마지막 조회로 남지 않는다")
    @Test
    void leave_success_concurrentLastViewedUpdateLeavesInactiveMembershipUnviewed() throws Exception {
        // given
        long ownerMemberId = saveMember("owner");
        long leavingMemberId = saveMember("racing");
        long workspaceId = saveWorkspace("경합 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerMemberId,
                "OWNER"
        );
        long leavingMembershipId = saveWorkspaceMember(
                workspaceId,
                leavingMemberId,
                "MEMBER"
        );
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Callable<WorkspaceErrorCode> leave = outcomeAfterBarrier(
                barrier,
                () -> workspaceLeaveService.leave(
                        leavingMemberId,
                        workspaceId
                )
        );
        Callable<WorkspaceErrorCode> updateLastViewed = outcomeAfterBarrier(
                barrier,
                () -> workspaceLastViewedService.update(
                        leavingMemberId,
                        workspaceId
                )
        );

        try {
            // when
            RaceResult result = awaitRaceResult(
                    executorService,
                    leave,
                    updateLastViewed
            );

            // then
            assertThat(result.leaveErrorCode()).isNull();
            assertThat(result.lastViewedErrorCode()).isIn(
                    null,
                    WorkspaceErrorCode.WORKSPACE_NOT_FOUND
            );
            MembershipState membership = membershipState(leavingMembershipId);
            assertThat(membership.leftAt()).isNotNull();
            assertThat(membership.lastViewed()).isFalse();
            assertThat(lastViewedCount(leavingMemberId)).isZero();
        } finally {
            executorService.shutdownNow();
        }
    }

    @DisplayName("탈퇴한 사용자가 실제 초대를 수락하면 기존 이력을 보존하고 새 MEMBER 멤버십을 만든다")
    @Test
    void leave_success_rejoinWithInvitationKeepsHistory() {
        // given
        long ownerMemberId = saveMember("owner");
        long leavingMemberId = saveMember("rejoin");
        long workspaceId = saveWorkspace("초대 재가입 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerMemberId,
                "OWNER"
        );
        long leftMembershipId = saveWorkspaceMember(
                workspaceId,
                leavingMemberId,
                "MEMBER"
        );
        WorkspaceInvitationResult invitation = workspaceInvitationService.issue(
                workspaceId,
                ownerMemberId
        );
        workspaceLeaveService.leave(
                leavingMemberId,
                workspaceId
        );
        MembershipState leftMembership = membershipState(leftMembershipId);

        // when
        WorkspaceInvitationAcceptanceResult result = workspaceInvitationAcceptanceService.accept(
                invitation.code(),
                "rejoin-remote",
                leavingMemberId
        );

        // then
        MembershipState activeMembership = latestMembershipState(
                workspaceId,
                leavingMemberId
        );
        assertThat(result.created()).isTrue();
        assertThat(leftMembership.id()).isEqualTo(leftMembershipId);
        assertThat(leftMembership.role()).isEqualTo("MEMBER");
        assertThat(leftMembership.joinedAt()).isEqualTo(JOINED_AT);
        assertThat(leftMembership.leftAt()).isNotNull();
        assertThat(activeMembership.id()).isNotEqualTo(leftMembershipId);
        assertThat(activeMembership.role()).isEqualTo("MEMBER");
        assertThat(activeMembership.leftAt()).isNull();
        assertThat(
                workspaceMembershipCount(
                        workspaceId,
                        leavingMemberId
                )
        ).isEqualTo(2);
        assertThat(activeMembershipCount(workspaceId)).isEqualTo(2);
    }

    @DisplayName("마지막 OWNER 탈퇴와 초대 수락 경합은 수락 후 탈퇴 거절 또는 삭제 후 초대 404로만 끝난다")
    @Test
    void leave_successOrConflict_whenRacingWithInvitationAccept() throws Exception {
        // given
        long ownerMemberId = saveMember("owner");
        long joiningMemberId = saveMember("joining");
        long workspaceId = saveWorkspace("탈퇴 수락 경합 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerMemberId,
                "OWNER"
        );
        WorkspaceInvitationResult invitation = workspaceInvitationService.issue(
                workspaceId,
                ownerMemberId
        );
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Callable<WorkspaceErrorCode> leave = outcomeAfterBarrier(
                barrier,
                () -> workspaceLeaveService.leave(
                        ownerMemberId,
                        workspaceId
                )
        );
        Callable<AcceptOutcome> accept = acceptOutcomeAfterBarrier(
                barrier,
                invitation.code(),
                joiningMemberId
        );

        try {
            // when
            LeaveAcceptRaceResult result = awaitLeaveAcceptRaceResult(
                    executorService,
                    leave,
                    accept
            );

            // then
            boolean acceptedThenLeaveRejected = result.acceptOutcome()
                    .accepted() && result.leaveErrorCode() == WorkspaceErrorCode.WORKSPACE_OWNER_TRANSFER_REQUIRED;
            boolean deletedThenAcceptRejected = !result.acceptOutcome()
                    .accepted()
                    && result.acceptOutcome()
                            .errorCode() == WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND
                    && result.leaveErrorCode() == null;
            assertThat(acceptedThenLeaveRejected || deletedThenAcceptRejected).isTrue();
            if (workspaceDeletedAt(workspaceId) == null) {
                assertThat(activeOwnerCount(workspaceId)).isGreaterThanOrEqualTo(1);
                assertThat(activeMembershipCount(workspaceId)).isEqualTo(2);
            } else {
                assertThat(activeMembershipCount(workspaceId)).isZero();
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    @DisplayName("마지막 멤버 탈퇴 후 같은 트랜잭션에서 예외가 나면 멤버십 탈퇴와 워크스페이스 삭제를 함께 롤백한다")
    @Test
    void leave_failure_rollsBackLastMemberLeaveWhenOuterTransactionFails() {
        // given
        long memberId = saveMember("rollback");
        long workspaceId = saveWorkspace("롤백 팀");
        long membershipId = saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );

        // when
        Throwable thrown = catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
            workspaceLeaveService.leave(
                    memberId,
                    workspaceId
            );
            throw new IllegalStateException("탈퇴 후 롤백 검증");
        }));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(membershipState(membershipId).leftAt()).isNull();
        assertThat(workspaceDeletedAt(workspaceId)).isNull();
        assertThat(activeMembershipCount(workspaceId)).isEqualTo(1);
    }

    @DisplayName("마지막 멤버 탈퇴로 삭제된 워크스페이스는 기존 초대 미리보기와 참여를 거절한다")
    @ParameterizedTest
    @ValueSource(strings = {"preview", "accept"})
    void invitation_failure_workspaceDeletedByLastMemberLeave(String operation) {
        // given
        long ownerId = saveMember("owner");
        long joiningId = saveMember("joining");
        long workspaceId = saveWorkspace("삭제 초대 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        WorkspaceInvitationResult invitation = workspaceInvitationService.issue(
                workspaceId,
                ownerId
        );
        workspaceLeaveService.leave(
                ownerId,
                workspaceId
        );

        // when
        Throwable thrown = catchThrowable(() -> {
            if (operation.equals("preview")) {
                workspaceInvitationService.preview(
                        invitation.code(),
                        "deleted-preview"
                );
            } else {
                workspaceInvitationAcceptanceService.accept(
                        invitation.code(),
                        "deleted-accept",
                        joiningId
                );
            }
        });

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND);
        assertThat(activeMembershipCount(workspaceId)).isZero();
        assertThat(
                workspaceMembershipCount(
                        workspaceId,
                        joiningId
                )
        ).isZero();
    }

    private Callable<WorkspaceErrorCode> outcomeAfterBarrier(
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
            } catch (WorkspaceException exception) {
                return (WorkspaceErrorCode) exception.getErrorCode();
            }
        };
    }

    private RaceResult awaitRaceResult(
            ExecutorService executorService,
            Callable<WorkspaceErrorCode> leave,
            Callable<WorkspaceErrorCode> updateLastViewed
    ) throws Exception {
        Future<WorkspaceErrorCode> leaveResult = executorService.submit(leave);
        Future<WorkspaceErrorCode> lastViewedResult = executorService.submit(updateLastViewed);
        return new RaceResult(
                leaveResult.get(
                        10,
                        TimeUnit.SECONDS
                ),
                lastViewedResult.get(
                        10,
                        TimeUnit.SECONDS
                )
        );
    }

    private Callable<AcceptOutcome> acceptOutcomeAfterBarrier(
            CyclicBarrier barrier,
            String credential,
            long memberId
    ) {
        return () -> {
            barrier.await(
                    5,
                    TimeUnit.SECONDS
            );
            try {
                return AcceptOutcome.accepted(
                        workspaceInvitationAcceptanceService.accept(
                                credential,
                                "accept-race-remote",
                                memberId
                        )
                );
            } catch (WorkspaceException exception) {
                return AcceptOutcome.rejected((WorkspaceErrorCode) exception.getErrorCode());
            }
        };
    }

    private LeaveAcceptRaceResult awaitLeaveAcceptRaceResult(
            ExecutorService executorService,
            Callable<WorkspaceErrorCode> leave,
            Callable<AcceptOutcome> accept
    ) throws Exception {
        Future<WorkspaceErrorCode> leaveResult = executorService.submit(leave);
        Future<AcceptOutcome> acceptResult = executorService.submit(accept);
        return new LeaveAcceptRaceResult(
                leaveResult.get(
                        10,
                        TimeUnit.SECONDS
                ),
                acceptResult.get(
                        10,
                        TimeUnit.SECONDS
                )
        );
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

    private void deleteWorkspace(
            long workspaceId,
            Instant deletedAt
    ) {
        jdbcClient.sql("""
                UPDATE workspaces
                SET deleted_at = CAST(:deletedAt AS TIMESTAMPTZ)
                WHERE id = :workspaceId
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "deletedAt",
                        deletedAt.toString()
                )
                .update();
    }

    private MembershipState membershipState(long membershipId) {
        return jdbcClient.sql("""
                SELECT id, role, joined_at, left_at, last_viewed
                FROM workspace_members
                WHERE id = :membershipId
                """)
                .param(
                        "membershipId",
                        membershipId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) -> membershipState(
                                resultSet,
                                rowNumber
                        )
                )
                .single();
    }

    private MembershipState membershipState(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime joinedAt = resultSet.getObject(
                "joined_at",
                OffsetDateTime.class
        );
        OffsetDateTime leftAt = resultSet.getObject(
                "left_at",
                OffsetDateTime.class
        );
        return new MembershipState(
                resultSet.getLong("id"),
                resultSet.getString("role"),
                joinedAt.toInstant(),
                leftAt == null ? null : leftAt.toInstant(),
                resultSet.getBoolean("last_viewed")
        );
    }

    private MembershipState latestMembershipState(
            long workspaceId,
            long memberId
    ) {
        return jdbcClient.sql("""
                SELECT id, role, joined_at, left_at, last_viewed
                FROM workspace_members
                WHERE workspace_id = :workspaceId
                  AND member_id = :memberId
                ORDER BY id DESC
                LIMIT 1
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) -> membershipState(
                                resultSet,
                                rowNumber
                        )
                )
                .single();
    }

    private Instant workspaceDeletedAt(long workspaceId) {
        return jdbcClient.sql("""
                SELECT deleted_at
                FROM workspaces
                WHERE id = :workspaceId
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) -> {
                            OffsetDateTime deletedAt = resultSet.getObject(
                                    "deleted_at",
                                    OffsetDateTime.class
                            );
                            return Optional.ofNullable(deletedAt)
                                    .map(OffsetDateTime::toInstant);
                        }
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

    private long workspaceMembershipCount(
            long workspaceId,
            long memberId
    ) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM workspace_members
                WHERE workspace_id = :workspaceId
                  AND member_id = :memberId
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
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

    private interface ThrowingOperation {

        void run();
    }

    private record MembershipState(
            long id,
            String role,
            Instant joinedAt,
            Instant leftAt,
            boolean lastViewed
    ) {
    }

    private record RaceResult(
            WorkspaceErrorCode leaveErrorCode,
            WorkspaceErrorCode lastViewedErrorCode
    ) {
    }

    private record LeaveAcceptRaceResult(
            WorkspaceErrorCode leaveErrorCode,
            AcceptOutcome acceptOutcome
    ) {
    }

    private record AcceptOutcome(
            WorkspaceInvitationAcceptanceResult result,
            WorkspaceErrorCode errorCode
    ) {

        private static AcceptOutcome accepted(WorkspaceInvitationAcceptanceResult result) {
            return new AcceptOutcome(
                    result,
                    null
            );
        }

        private static AcceptOutcome rejected(WorkspaceErrorCode errorCode) {
            return new AcceptOutcome(
                    null,
                    errorCode
            );
        }

        private boolean accepted() {
            return result != null;
        }
    }
}
