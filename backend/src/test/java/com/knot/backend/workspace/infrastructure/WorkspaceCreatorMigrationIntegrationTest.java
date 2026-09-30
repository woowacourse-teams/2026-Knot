package com.knot.backend.workspace.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class WorkspaceCreatorMigrationIntegrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("pgvector/pgvector:pg18")
            .withDatabaseName("knot_workspace_creator_migration_test")
            .withUsername("knot")
            .withPassword("knot");

    @BeforeEach
    void migrateToPreviousVersion() {
        Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        flywayAt("14").migrate();
    }

    @Test
    @DisplayName("V15는 기존 OWNER 한 명을 최초 생성자로 이관하고 삭제 시각을 비워 둔다")
    void migrate_success_existingOwnerBackfilled() throws SQLException {
        // given
        insertWorkspaceWithMembers(1);

        // when
        flywayAt("15").migrate();

        // then
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT created_by_member_id, deleted_at
                        FROM workspaces
                        WHERE id = 1
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong("created_by_member_id")).isEqualTo(1L);
            assertThat(result.getObject("deleted_at")).isNull();
        }
    }

    @Test
    @DisplayName("기존 OWNER가 없으면 V15 이관을 중단하고 원본 행을 보존한다")
    void migrate_failure_missingOwner() throws SQLException {
        // given
        insertWorkspaceWithMembers(0);

        // when
        Throwable migrationFailure = org.assertj.core.api.Assertions.catchThrowable(() -> flywayAt("15").migrate());

        // then
        assertThat(migrationFailure).isInstanceOf(FlywayException.class);
        assertThat(countWorkspaces()).isEqualTo(1);
        assertThat(hasCreatorColumn()).isFalse();
    }

    @Test
    @DisplayName("기존 OWNER가 둘이면 V15 이관을 중단하고 원본 행을 보존한다")
    void migrate_failure_multipleOwners() throws SQLException {
        // given
        insertWorkspaceWithMembers(2);

        // when
        Throwable migrationFailure = org.assertj.core.api.Assertions.catchThrowable(() -> flywayAt("15").migrate());

        // then
        assertThat(migrationFailure).isInstanceOf(FlywayException.class);
        assertThat(countWorkspaces()).isEqualTo(1);
        assertThat(hasCreatorColumn()).isFalse();
    }

    private Flyway flywayAt(String version) {
        return Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load();
    }

    private void insertWorkspaceWithMembers(int ownerCount) throws SQLException {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO members (nickname) VALUES ('creator'), ('other')");
            statement.executeUpdate("INSERT INTO workspaces (name, created_at) VALUES ('기존 팀', CURRENT_TIMESTAMP)");
            for (int index = 1; index <= ownerCount; index++) {
                statement.executeUpdate("""
                        INSERT INTO workspace_members (workspace_id, member_id, role, joined_at, last_viewed)
                        VALUES (1, """ + index + ", 'OWNER', CURRENT_TIMESTAMP, FALSE)");
            }
        }
    }

    private long countWorkspaces() throws SQLException {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM workspaces")) {
            result.next();
            return result.getLong(1);
        }
    }

    private boolean hasCreatorColumn() throws SQLException {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_name = 'workspaces' AND column_name = 'created_by_member_id'
                        """)) {
            result.next();
            return result.getLong(1) > 0;
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        );
    }
}
