package com.knot.backend.workspace.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class WorkspaceLeaveMigrationIntegrationTest {
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("pgvector/pgvector:pg18")
            .withDatabaseName("knot_workspace_leave_migration_test")
            .withUsername("knot")
            .withPassword("knot");

    @DisplayName("V14 스키마를 V17로 업그레이드하면 탈퇴 이력과 삭제 워크스페이스 제약을 만든다")
    @Test
    void migrate_success_v14SchemaToV17() throws SQLException {
        // given
        cleanAndMigrate(MigrationVersion.fromVersion("14"));

        // when
        MigrateResult result = configureFlyway().migrate();

        // then
        assertThat(result.success).isTrue();
        assertThat(appliedVersions()).containsExactly(
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "9",
                "10",
                "11",
                "12",
                "13",
                "14",
                "15",
                "17"
        );
        assertThat(columnNames("workspaces")).contains("deleted_at");
        assertThat(columnNames("workspace_members")).contains("left_at");
        assertThat(constraintNames()).contains(
                "chk_workspaces_deleted_at_after_created_at",
                "chk_workspace_members_left_at_after_joined_at",
                "chk_workspace_members_left_not_last_viewed"
        );
        assertThat(indexNames()).contains(
                "uk_workspace_members_active_workspace_member",
                "uk_workspace_members_member_last_viewed"
        );
    }

    @DisplayName("V14 기존 워크스페이스와 멤버십 Row는 V15 업그레이드 후 기존 값을 보존한다")
    @Test
    void migrate_success_preservesExistingV14Rows() throws SQLException {
        // given
        cleanAndMigrate(MigrationVersion.fromVersion("14"));
        long memberId = insertMember("preserve");
        long workspaceId = insertWorkspace("기존 보존 팀");
        long membershipId = insertV14WorkspaceMember(
                workspaceId,
                memberId,
                "OWNER",
                true
        );

        // when
        MigrateResult result = configureFlyway().migrate();

        // then
        WorkspaceMemberRow row = workspaceMemberRow(membershipId);
        assertThat(result.success).isTrue();
        assertThat(workspaceDeletedAt(workspaceId)).isNull();
        assertThat(row.workspaceId()).isEqualTo(workspaceId);
        assertThat(row.memberId()).isEqualTo(memberId);
        assertThat(row.role()).isEqualTo("OWNER");
        assertThat(row.joinedAt()).isEqualTo(Instant.parse("2026-08-31T00:01:00Z"));
        assertThat(row.lastViewed()).isTrue();
        assertThat(row.leftAt()).isNull();
    }

    @DisplayName("탈퇴한 멤버십 이력이 있어도 같은 사용자에게 새 활성 멤버십을 저장할 수 있다")
    @Test
    void migrate_success_allowsRejoinAfterLeftMembership() throws SQLException {
        // given
        cleanAndMigrate();
        long memberId = insertMember("rejoin");
        long workspaceId = insertWorkspace("재가입 팀");
        insertWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER",
                true
        );

        // when
        insertWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER",
                false
        );

        // then
        assertThat(
                workspaceMembershipCount(
                        workspaceId,
                        memberId
                )
        ).isEqualTo(2);
        assertThat(
                activeWorkspaceMembershipCount(
                        workspaceId,
                        memberId
                )
        ).isEqualTo(1);
    }

    @DisplayName("같은 사용자와 워크스페이스의 활성 멤버십은 하나만 허용한다")
    @Test
    void migrate_failure_rejectsDuplicateActiveMembership() throws SQLException {
        // given
        cleanAndMigrate();
        long memberId = insertMember("duplicate");
        long workspaceId = insertWorkspace("중복 팀");
        insertWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER",
                false
        );

        // when
        Throwable thrown = catchThrowable(
                () -> insertWorkspaceMember(
                        workspaceId,
                        memberId,
                        "MEMBER",
                        false
                )
        );

        // then
        assertThat(thrown).isInstanceOf(SQLException.class);
        assertThat(
                activeWorkspaceMembershipCount(
                        workspaceId,
                        memberId
                )
        ).isEqualTo(1);
    }

    @DisplayName("탈퇴한 멤버십은 마지막 조회 상태로 저장할 수 없다")
    @Test
    void migrate_failure_rejectsLeftLastViewedMembership() throws SQLException {
        // given
        cleanAndMigrate();
        long memberId = insertMember("left-viewed");
        long workspaceId = insertWorkspace("마지막 조회 탈퇴 팀");
        long membershipId = insertWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER",
                false
        );

        // when
        Throwable thrown = catchThrowable(() -> executeUpdate("""
                UPDATE workspace_members
                SET last_viewed = TRUE,
                    left_at = joined_at + INTERVAL '1 second'
                WHERE id = %d
                """.formatted(membershipId)));

        // then
        assertThat(thrown).isInstanceOf(SQLException.class);
        assertThat(queryBoolean("""
                SELECT last_viewed = FALSE AND left_at IS NULL
                FROM workspace_members
                WHERE id = %d
                """.formatted(membershipId))).isTrue();
    }

    @DisplayName("워크스페이스 삭제 시각은 생성 시각보다 빠르게 저장할 수 없다")
    @Test
    void migrate_failure_rejectsDeletedAtBeforeCreatedAt() throws SQLException {
        // given
        cleanAndMigrate();
        long workspaceId = insertWorkspace("삭제 시각 팀");

        // when
        Throwable thrown = catchThrowable(() -> executeUpdate("""
                UPDATE workspaces
                SET deleted_at = created_at - INTERVAL '1 second'
                WHERE id = %d
                """.formatted(workspaceId)));

        // then
        assertThat(thrown).isInstanceOf(SQLException.class);
        assertThat(queryBoolean("""
                SELECT deleted_at IS NULL
                FROM workspaces
                WHERE id = %d
                """.formatted(workspaceId))).isTrue();
    }

    @DisplayName("멤버십 탈퇴 시각은 참여 시각보다 빠르게 저장할 수 없다")
    @Test
    void migrate_failure_rejectsLeftAtBeforeJoinedAt() throws SQLException {
        // given
        cleanAndMigrate();
        long memberId = insertMember("left-before-joined");
        long workspaceId = insertWorkspace("탈퇴 시각 팀");

        // when
        Throwable thrown = catchThrowable(
                () -> executeUpdate(
                        """
                                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at, left_at)
                                VALUES (
                                    %d,
                                    %d,
                                    'MEMBER',
                                    TIMESTAMPTZ '2026-08-31 00:01:00+00',
                                    TIMESTAMPTZ '2026-08-31 00:00:59+00'
                                )
                                """.formatted(
                                workspaceId,
                                memberId
                        )
                )
        );

        // then
        assertThat(thrown).isInstanceOf(SQLException.class);
        assertThat(
                activeWorkspaceMembershipCount(
                        workspaceId,
                        memberId
                )
        ).isZero();
    }

    private void cleanAndMigrate(MigrationVersion target) {
        Flyway cleanableFlyway = Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations(MIGRATION_LOCATION)
                .cleanDisabled(false)
                .load();
        cleanableFlyway.clean();
        configureFlyway(target).migrate();
    }

    private void cleanAndMigrate() {
        Flyway cleanableFlyway = Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations(MIGRATION_LOCATION)
                .cleanDisabled(false)
                .load();
        cleanableFlyway.clean();
        configureFlyway().migrate();
    }

    private Flyway configureFlyway(MigrationVersion target) {
        return Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations(MIGRATION_LOCATION)
                .target(target)
                .load();
    }

    private Flyway configureFlyway() {
        return Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations(MIGRATION_LOCATION)
                .load();
    }

    private List<String> appliedVersions() {
        return Arrays.stream(
                configureFlyway().info()
                        .applied()
        )
                .map(MigrationInfo::getVersion)
                .map(MigrationVersion::getVersion)
                .toList();
    }

    private List<String> columnNames(String tableName) throws SQLException {
        return queryStrings("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = '%s'
                ORDER BY ordinal_position
                """.formatted(tableName));
    }

    private List<String> constraintNames() throws SQLException {
        return queryStrings("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = 'public'
                ORDER BY constraint_name
                """);
    }

    private List<String> indexNames() throws SQLException {
        return queryStrings("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                ORDER BY indexname
                """);
    }

    private List<String> queryStrings(String query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private long insertMember(String nickname) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES (?, NULL)
                RETURNING id
                """)) {
            statement.setString(
                    1,
                    nickname
            );
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private long insertWorkspace(String name) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workspaces (name, created_at)
                VALUES (?, TIMESTAMPTZ '2026-08-31 00:00:00+00')
                RETURNING id
                """)) {
            statement.setString(
                    1,
                    name
            );
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private long insertV14WorkspaceMember(
            long workspaceId,
            long memberId,
            String role,
            boolean lastViewed
    ) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at, last_viewed)
                VALUES (
                    ?,
                    ?,
                    ?,
                    TIMESTAMPTZ '2026-08-31 00:01:00+00',
                    ?
                )
                RETURNING id
                """)) {
            statement.setLong(
                    1,
                    workspaceId
            );
            statement.setLong(
                    2,
                    memberId
            );
            statement.setString(
                    3,
                    role
            );
            statement.setBoolean(
                    4,
                    lastViewed
            );
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private long insertWorkspaceMember(
            long workspaceId,
            long memberId,
            String role,
            boolean left
    ) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at, left_at)
                VALUES (
                    ?,
                    ?,
                    ?,
                    TIMESTAMPTZ '2026-08-31 00:01:00+00',
                    CASE WHEN ? THEN TIMESTAMPTZ '2026-08-31 00:02:00+00' ELSE NULL END
                )
                RETURNING id
                """)) {
            statement.setLong(
                    1,
                    workspaceId
            );
            statement.setLong(
                    2,
                    memberId
            );
            statement.setString(
                    3,
                    role
            );
            statement.setBoolean(
                    4,
                    left
            );
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private WorkspaceMemberRow workspaceMemberRow(long membershipId) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    workspace_id,
                    member_id,
                    role,
                    joined_at,
                    last_viewed,
                    left_at
                FROM workspace_members
                WHERE id = ?
                """)) {
            statement.setLong(
                    1,
                    membershipId
            );
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                OffsetDateTime joinedAt = resultSet.getObject(
                        "joined_at",
                        OffsetDateTime.class
                );
                OffsetDateTime leftAt = resultSet.getObject(
                        "left_at",
                        OffsetDateTime.class
                );
                return new WorkspaceMemberRow(
                        resultSet.getLong("workspace_id"),
                        resultSet.getLong("member_id"),
                        resultSet.getString("role"),
                        joinedAt.toInstant(),
                        resultSet.getBoolean("last_viewed"),
                        leftAt == null ? null : leftAt.toInstant()
                );
            }
        }
    }

    private Instant workspaceDeletedAt(long workspaceId) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT deleted_at
                FROM workspaces
                WHERE id = ?
                """)) {
            statement.setLong(
                    1,
                    workspaceId
            );
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                OffsetDateTime deletedAt = resultSet.getObject(
                        "deleted_at",
                        OffsetDateTime.class
                );
                return deletedAt == null ? null : deletedAt.toInstant();
            }
        }
    }

    private long workspaceMembershipCount(
            long workspaceId,
            long memberId
    ) throws SQLException {
        return queryLong(
                """
                        SELECT count(*)
                        FROM workspace_members
                        WHERE workspace_id = %d
                          AND member_id = %d
                        """.formatted(
                        workspaceId,
                        memberId
                )
        );
    }

    private long activeWorkspaceMembershipCount(
            long workspaceId,
            long memberId
    ) throws SQLException {
        return queryLong(
                """
                        SELECT count(*)
                        FROM workspace_members
                        WHERE workspace_id = %d
                          AND member_id = %d
                          AND left_at IS NULL
                        """.formatted(
                        workspaceId,
                        memberId
                )
        );
    }

    private boolean queryBoolean(String query) throws SQLException {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private long queryLong(String query) throws SQLException {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private void executeUpdate(String query) throws SQLException {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(query);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        );
    }

    private record WorkspaceMemberRow(
            long workspaceId,
            long memberId,
            String role,
            Instant joinedAt,
            boolean lastViewed,
            Instant leftAt
    ) {
    }
}
