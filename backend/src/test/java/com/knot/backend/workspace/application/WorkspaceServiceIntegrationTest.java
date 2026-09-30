package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.dto.result.WorkspaceCreateResult;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = AutowireMode.ALL)
class WorkspaceServiceIntegrationTest {
    private final WorkspaceService workspaceService;
    private final JdbcClient jdbcClient;

    WorkspaceServiceIntegrationTest(
            WorkspaceService workspaceService,
            JdbcClient jdbcClient
    ) {
        this.workspaceService = workspaceService;
        this.jdbcClient = jdbcClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient
                .sql("TRUNCATE TABLE workspace_members, workspaces, oauth_identities, members RESTART IDENTITY CASCADE")
                .update();
    }

    @Test
    @DisplayName("워크스페이스를 생성하면 워크스페이스와 OWNER 멤버십을 함께 저장한다")
    void create_success() {
        // given
        long memberId = saveMember("octocat");

        // when
        WorkspaceCreateResult result = workspaceService.create(
                memberId,
                "Knot 팀"
        );

        // then
        assertThat(result.id()).isPositive();
        assertThat(workspaceName(result.id())).isEqualTo("Knot 팀");
        assertThat(workspaceMemberRole(result.id())).isEqualTo("OWNER");
        assertThat(workspaceMemberId(result.id())).isEqualTo(memberId);
        assertThat(workspaceCreatorId(result.id())).isEqualTo(memberId);
        assertThat(workspaceCreatedAt(result.id())).isEqualTo(workspaceJoinedAt(result.id()));
    }

    @Test
    @DisplayName("같은 member가 같은 이름으로 다시 생성하면 별도 워크스페이스를 저장한다")
    void create_success_duplicateWorkspaceName() {
        // given
        long memberId = saveMember("octocat");
        WorkspaceCreateResult first = workspaceService.create(
                memberId,
                "Knot 팀"
        );

        // when
        WorkspaceCreateResult second = workspaceService.create(
                memberId,
                "Knot 팀"
        );

        // then
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(count("workspaces")).isEqualTo(2);
        assertThat(count("workspace_members")).isEqualTo(2);
    }

    @Test
    @DisplayName("멤버십 저장이 실패하면 먼저 저장한 워크스페이스도 rollback한다")
    void create_failure_membershipSaveRollsBackWorkspace() {
        // given
        long memberId = saveMember("octocat");
        jdbcClient.sql("ALTER TABLE workspace_members ADD CONSTRAINT chk_test_reject_owner CHECK (role <> 'OWNER')")
                .update();

        try {
            // when
            Throwable thrown = catchThrowable(
                    () -> workspaceService.create(
                            memberId,
                            "Knot 팀"
                    )
            );

            // then
            assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(count("workspaces")).isZero();
            assertThat(count("workspace_members")).isZero();
        } finally {
            jdbcClient.sql("ALTER TABLE workspace_members DROP CONSTRAINT chk_test_reject_owner")
                    .update();
        }
    }

    @Test
    @DisplayName("직접 생성한 워크스페이스가 세 개면 네 번째 생성을 거절한다")
    void create_failure_creationLimitExceeded() {
        // given
        long memberId = saveMember("octocat");
        workspaceService.create(
                memberId,
                "첫 팀"
        );
        workspaceService.create(
                memberId,
                "두 번째 팀"
        );
        workspaceService.create(
                memberId,
                "세 번째 팀"
        );

        // when
        Throwable thrown = catchThrowable(
                () -> workspaceService.create(
                        memberId,
                        "네 번째 팀"
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                WorkspaceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(WorkspaceErrorCode.WORKSPACE_CREATION_LIMIT_EXCEEDED)
        );
        assertThat(count("workspaces")).isEqualTo(3);
        assertThat(count("workspace_members")).isEqualTo(3);
    }

    @Test
    @DisplayName("초대로 참여한 워크스페이스는 직접 생성 한도에 포함하지 않는다")
    void create_success_invitedMembershipDoesNotCount() {
        // given
        long inviterId = saveMember("inviter");
        long memberId = saveMember("octocat");
        long invitedWorkspaceId = workspaceService.create(
                inviterId,
                "초대한 팀"
        )
                .id();
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at, last_viewed)
                VALUES (:workspaceId, :memberId, 'MEMBER', CURRENT_TIMESTAMP, FALSE)
                """)
                .param(
                        "workspaceId",
                        invitedWorkspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .update();
        workspaceService.create(
                memberId,
                "첫 팀"
        );
        workspaceService.create(
                memberId,
                "두 번째 팀"
        );

        // when
        WorkspaceCreateResult result = workspaceService.create(
                memberId,
                "세 번째 팀"
        );

        // then
        assertThat(result.id()).isPositive();
        assertThat(workspaceCreatorId(result.id())).isEqualTo(memberId);
        assertThat(count("workspaces")).isEqualTo(4);
    }

    @Test
    @DisplayName("생성자가 OWNER 멤버십에서 나가도 직접 생성한 워크스페이스는 한도에 남는다")
    void create_failure_creatorDepartureDoesNotResetLimit() {
        // given
        long memberId = saveMember("octocat");
        long firstWorkspaceId = workspaceService.create(
                memberId,
                "첫 팀"
        )
                .id();
        workspaceService.create(
                memberId,
                "두 번째 팀"
        );
        workspaceService.create(
                memberId,
                "세 번째 팀"
        );
        jdbcClient.sql("DELETE FROM workspace_members WHERE workspace_id = :workspaceId")
                .param(
                        "workspaceId",
                        firstWorkspaceId
                )
                .update();

        // when
        Throwable thrown = catchThrowable(
                () -> workspaceService.create(
                        memberId,
                        "네 번째 팀"
                )
        );

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                WorkspaceException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(WorkspaceErrorCode.WORKSPACE_CREATION_LIMIT_EXCEEDED)
        );
        assertThat(count("workspaces")).isEqualTo(3);
        assertThat(count("workspace_members")).isEqualTo(2);
    }

    @Test
    @DisplayName("논리 삭제된 워크스페이스는 직접 생성 한도에서 제외한다")
    void create_success_deletedWorkspaceDoesNotCount() {
        // given
        long memberId = saveMember("octocat");
        long deletedWorkspaceId = workspaceService.create(
                memberId,
                "삭제된 팀"
        )
                .id();
        workspaceService.create(
                memberId,
                "두 번째 팀"
        );
        workspaceService.create(
                memberId,
                "세 번째 팀"
        );
        jdbcClient.sql("UPDATE workspaces SET deleted_at = CURRENT_TIMESTAMP WHERE id = :workspaceId")
                .param(
                        "workspaceId",
                        deletedWorkspaceId
                )
                .update();

        // when
        WorkspaceCreateResult result = workspaceService.create(
                memberId,
                "새 팀"
        );

        // then
        assertThat(result.id()).isPositive();
        assertThat(count("workspaces")).isEqualTo(4);
    }

    @Test
    @DisplayName("두 개를 만든 회원의 동시 생성 요청은 하나만 성공한다")
    void create_success_concurrentRequestsRespectLimit() throws Exception {
        // given
        long memberId = saveMember("octocat");
        workspaceService.create(
                memberId,
                "첫 팀"
        );
        workspaceService.create(
                memberId,
                "두 번째 팀"
        );
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Throwable> create = () -> {
            barrier.await();
            return catchThrowable(
                    () -> workspaceService.create(
                            memberId,
                            "동시성 팀"
                    )
            );
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // when
            List<Future<Throwable>> outcomes = List.of(
                    executor.submit(create),
                    executor.submit(create)
            );

            // then
            Throwable first = outcomes.get(0)
                    .get();
            Throwable second = outcomes.get(1)
                    .get();
            assertThat((first == null) != (second == null)).isTrue();
            Throwable rejected = first == null ? second : first;
            assertThat(rejected).isInstanceOfSatisfying(
                    WorkspaceException.class,
                    exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(WorkspaceErrorCode.WORKSPACE_CREATION_LIMIT_EXCEEDED)
            );
            assertThat(count("workspaces")).isEqualTo(3);
            assertThat(count("workspace_members")).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
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

    private String workspaceName(long workspaceId) {
        return jdbcClient.sql("SELECT name FROM workspaces WHERE id = :workspaceId")
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(String.class)
                .single();
    }

    private String workspaceMemberRole(long workspaceId) {
        return jdbcClient.sql("SELECT role FROM workspace_members WHERE workspace_id = :workspaceId")
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(String.class)
                .single();
    }

    private long workspaceMemberId(long workspaceId) {
        return jdbcClient.sql("SELECT member_id FROM workspace_members WHERE workspace_id = :workspaceId")
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
    }

    private long workspaceCreatorId(long workspaceId) {
        return jdbcClient.sql("SELECT created_by_member_id FROM workspaces WHERE id = :workspaceId")
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
    }

    private Instant workspaceCreatedAt(long workspaceId) {
        return jdbcClient.sql("SELECT created_at FROM workspaces WHERE id = :workspaceId")
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Instant.class)
                .single();
    }

    private Instant workspaceJoinedAt(long workspaceId) {
        return jdbcClient.sql("SELECT joined_at FROM workspace_members WHERE workspace_id = :workspaceId")
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Instant.class)
                .single();
    }

    private int count(String tableName) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + tableName)
                .query(Integer.class)
                .single();
    }
}
