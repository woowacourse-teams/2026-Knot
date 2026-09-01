package com.knot.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
class FlywayMigrationUpgradeIntegrationTest {
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18.4")
            .withDatabaseName("knot_migration_upgrade_test")
            .withUsername("knot")
            .withPassword("knot");

    @DisplayName("V9 스키마를 V12 콘텐츠 Import heartbeat 스키마로 업그레이드한다")
    @Test
    void migrate_success_v9ToContentImportSchema() throws SQLException {
        // given
        Flyway v9Flyway = configureFlyway(MigrationVersion.fromVersion("9"));
        v9Flyway.migrate();
        List<String> versionsBeforeUpgrade = appliedVersions(v9Flyway);
        List<String> tablesBeforeUpgrade = schemaObjectNames("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                ORDER BY table_name
                """);
        Flyway v11Flyway = configureFlyway(MigrationVersion.fromVersion("11"));
        MigrateResult v11Result = v11Flyway.migrate();
        insertImportRun(
                "migration-running",
                "RUNNING"
        );
        insertImportRun(
                "migration-pending",
                "PENDING"
        );
        Flyway latestFlyway = configureFlyway();

        // when
        MigrateResult result = latestFlyway.migrate();

        // then
        assertThat(versionsBeforeUpgrade).containsExactly(
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "9"
        );
        assertThat(tablesBeforeUpgrade).doesNotContain(
                "content_import_runs",
                "imported_pages",
                "imported_page_publications"
        );
        assertThat(result.success).isTrue();
        assertThat(v11Result.success).isTrue();
        assertThat(v11Result.migrationsExecuted).isEqualTo(2);
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(appliedVersions(latestFlyway)).containsExactly(
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "9",
                "10",
                "11",
                "12"
        );
        assertThat(schemaObjectNames("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                ORDER BY table_name
                """)).contains(
                "content_import_runs",
                "imported_pages",
                "imported_page_publications"
        );
        assertThat(schemaObjectNames("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = 'public'
                ORDER BY constraint_name
                """)).contains(
                "uk_content_source_connections_id_workspace",
                "pk_content_import_runs",
                "fk_content_import_runs_connection",
                "fk_content_import_runs_requester_membership",
                "chk_content_import_runs_status",
                "chk_content_import_runs_completed_page_counts",
                "uk_content_import_runs_id_workspace",
                "uk_content_import_runs_id_workspace_status",
                "chk_content_import_runs_heartbeat",
                "pk_imported_pages",
                "uk_imported_pages_workspace_run_external_page",
                "fk_imported_pages_import_run",
                "fk_imported_pages_parent",
                "chk_imported_pages_timestamps",
                "pk_imported_page_publications",
                "chk_imported_page_publications_status",
                "fk_imported_page_publications_import_run"
        );
        assertThat(schemaObjectNames("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                ORDER BY indexname
                """)).contains(
                "uk_workspace_members_member_last_viewed",
                "uk_content_import_runs_one_active",
                "idx_content_import_runs_workspace_created",
                "idx_content_import_runs_running_heartbeat",
                "idx_imported_pages_workspace_run_order"
        );
        assertThat(schemaObjectNames("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                    AND table_name = 'content_import_runs'
                ORDER BY ordinal_position
                """)).contains("last_heartbeat_at");
        assertThat(queryBoolean("""
                SELECT last_heartbeat_at > started_at
                FROM content_import_runs
                WHERE status = 'RUNNING'
                """)).isTrue();
        assertThat(queryBoolean("""
                SELECT last_heartbeat_at IS NOT NULL
                FROM content_import_runs
                WHERE status = 'PENDING'
                """)).isTrue();
        executeUpdate("""
                UPDATE content_import_runs
                SET status = 'FAILED',
                    completed_at = CURRENT_TIMESTAMP
                WHERE status = 'RUNNING'
                """);
        assertThat(queryBoolean("""
                SELECT status = 'FAILED' AND last_heartbeat_at IS NOT NULL
                FROM content_import_runs
                WHERE status = 'FAILED'
                """)).isTrue();
        executeUpdate("""
                UPDATE content_import_runs
                SET status = 'RUNNING',
                    started_at = CURRENT_TIMESTAMP
                WHERE status = 'PENDING'
                """);
        assertThat(queryBoolean("""
                SELECT status = 'RUNNING' AND last_heartbeat_at IS NOT NULL
                FROM content_import_runs
                WHERE status = 'RUNNING'
                """)).isTrue();
        executeUpdate("""
                UPDATE content_import_runs
                SET last_heartbeat_at = CURRENT_TIMESTAMP - INTERVAL '2 hours'
                WHERE status = 'RUNNING'
                """);
        assertThat(queryBoolean("""
                SELECT NOT EXISTS (
                    SELECT 1
                    FROM content_import_runs
                    WHERE status = 'RUNNING'
                        AND last_heartbeat_at <= CURRENT_TIMESTAMP - INTERVAL '1 hour'
                        AND started_at <= CURRENT_TIMESTAMP - INTERVAL '1 hour'
                )
                """)).isTrue();
        executeUpdate("""
                INSERT INTO content_import_runs (
                    workspace_id,
                    content_source_connection_id,
                    requested_by_member_id,
                    status,
                    processed_page_count,
                    created_at
                )
                SELECT
                    workspace_id,
                    content_source_connection_id,
                    requested_by_member_id,
                    'PENDING',
                    0,
                    CURRENT_TIMESTAMP
                FROM content_import_runs
                WHERE status = 'FAILED'
                """);
        assertThat(queryBoolean("""
                SELECT last_heartbeat_at IS NOT NULL
                FROM content_import_runs
                WHERE status = 'PENDING'
                """)).isTrue();
        executeUpdate("""
                UPDATE content_import_runs
                SET status = 'RUNNING',
                    started_at = CURRENT_TIMESTAMP
                WHERE status = 'PENDING'
                """);
        assertThat(queryBoolean("""
                SELECT BOOL_AND(last_heartbeat_at IS NOT NULL)
                FROM content_import_runs
                WHERE status = 'RUNNING'
                """)).isTrue();
    }

    private void insertImportRun(
            String label,
            String status
    ) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                WITH inserted_member AS (
                    INSERT INTO members (nickname, profile_image_url)
                    VALUES (?, NULL)
                    RETURNING id
                ), inserted_workspace AS (
                    INSERT INTO workspaces (name, created_at)
                    VALUES (?, CURRENT_TIMESTAMP - INTERVAL '3 hours')
                    RETURNING id
                ), inserted_membership AS (
                    INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                    SELECT
                        inserted_workspace.id,
                        inserted_member.id,
                        'OWNER',
                        CURRENT_TIMESTAMP - INTERVAL '3 hours'
                    FROM inserted_workspace, inserted_member
                    RETURNING workspace_id, member_id
                ), inserted_connection AS (
                    INSERT INTO content_source_connections (
                        workspace_id,
                        provider,
                        access_credential_ciphertext,
                        external_source_id,
                        provider_connection_id,
                        authorization_owner_type,
                        authorizing_member_id,
                        created_at,
                        updated_at
                    )
                    SELECT
                        workspace_id,
                        'NOTION',
                        'ciphertext',
                        ?,
                        ?,
                        'WORKSPACE',
                        member_id,
                        CURRENT_TIMESTAMP - INTERVAL '3 hours',
                        CURRENT_TIMESTAMP - INTERVAL '3 hours'
                    FROM inserted_membership
                    RETURNING id, workspace_id
                )
                INSERT INTO content_import_runs (
                    workspace_id,
                    content_source_connection_id,
                    requested_by_member_id,
                    status,
                    processed_page_count,
                    started_at,
                    created_at
                )
                SELECT
                    inserted_connection.workspace_id,
                    inserted_connection.id,
                    inserted_membership.member_id,
                    ?,
                    0,
                    CASE
                        WHEN ? = 'RUNNING' THEN CURRENT_TIMESTAMP - INTERVAL '2 hours'
                        ELSE NULL
                    END,
                    CURRENT_TIMESTAMP - INTERVAL '3 hours'
                FROM inserted_connection, inserted_membership
                """)) {
            statement.setString(
                    1,
                    label
            );
            statement.setString(
                    2,
                    label
            );
            statement.setString(
                    3,
                    label + "-source"
            );
            statement.setString(
                    4,
                    label + "-bot"
            );
            statement.setString(
                    5,
                    status
            );
            statement.setString(
                    6,
                    status
            );
            statement.executeUpdate();
        }
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

    private List<String> appliedVersions(Flyway flyway) {
        return Arrays.stream(
                flyway.info()
                        .applied()
        )
                .map(MigrationInfo::getVersion)
                .map(MigrationVersion::getVersion)
                .toList();
    }

    private List<String> schemaObjectNames(String query) throws SQLException {
        List<String> names = new ArrayList<>();

        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
        }

        return names;
    }

    private boolean queryBoolean(String query) throws SQLException {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            resultSet.next();
            return resultSet.getBoolean(1);
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
}
