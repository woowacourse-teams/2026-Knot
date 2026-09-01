package com.knot.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
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

    @DisplayName("V9까지 적용된 스키마를 V10과 V11의 Notion Import 스키마로 업그레이드한다")
    @Test
    void migrate_success_v9ToNotionImportSchema() throws SQLException {
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
                "notion_import_runs",
                "notion_pages",
                "notion_page_publications"
        );
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(2);
        assertThat(appliedVersions(latestFlyway)).containsExactly(
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "9",
                "10",
                "11"
        );
        assertThat(schemaObjectNames("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                ORDER BY table_name
                """)).contains(
                "notion_import_runs",
                "notion_pages",
                "notion_page_publications"
        );
        assertThat(schemaObjectNames("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = 'public'
                ORDER BY constraint_name
                """)).contains(
                "uk_content_source_connections_id_workspace",
                "pk_notion_import_runs",
                "fk_notion_import_runs_connection",
                "fk_notion_import_runs_requester_membership",
                "chk_notion_import_runs_status",
                "uk_notion_import_runs_id_workspace",
                "pk_notion_pages",
                "uk_notion_pages_workspace_run_notion_page",
                "fk_notion_pages_import_run",
                "fk_notion_pages_parent",
                "chk_notion_pages_timestamps",
                "pk_notion_page_publications",
                "fk_notion_page_publications_import_run"
        );
        assertThat(schemaObjectNames("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                ORDER BY indexname
                """)).contains(
                "uk_workspace_members_member_last_viewed",
                "uk_notion_import_runs_one_active",
                "idx_notion_import_runs_workspace_created",
                "idx_notion_pages_workspace_run_order"
        );
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

        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        ); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
        }

        return names;
    }
}
