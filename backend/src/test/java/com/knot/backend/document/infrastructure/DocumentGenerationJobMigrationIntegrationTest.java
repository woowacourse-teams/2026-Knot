package com.knot.backend.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class DocumentGenerationJobMigrationIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("실패 시각을 알 수 없는 기존 FAILED가 있으면 이행을 중단하고 행을 보존한다")
    void migrate_failure_unverifiedLegacyFailure() throws Exception {
        // given
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE SCHEMA knot_test_job_legacy;
                    CREATE TABLE knot_test_job_legacy.document_generation_jobs (
                        id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
                    );
                    INSERT INTO knot_test_job_legacy.document_generation_jobs VALUES (1, 'FAILED', NOW(), NOW())
                    """);
            try {
                Flyway flyway = legacyMigration();

                // when & then
                assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class)
                        .hasStackTraceContaining("verified failure timestamps");
                try (ResultSet rows = statement.executeQuery(
                        "SELECT status FROM knot_test_job_legacy.document_generation_jobs WHERE id = 1"
                )) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("status")).isEqualTo("FAILED");
                }
            } finally {
                statement.execute("DROP SCHEMA knot_test_job_legacy CASCADE");
            }
        }
    }

    @Test
    @DisplayName("기존 QUEUED 작업은 시각·상태를 유지하고 실패 이력은 null로 이행한다")
    void migrate_success_legacyQueuedJob() throws Exception {
        // given
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE SCHEMA knot_test_job_legacy;
                    CREATE TABLE knot_test_job_legacy.document_generation_jobs (
                        id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
                    );
                    INSERT INTO knot_test_job_legacy.document_generation_jobs
                    VALUES (1, 'QUEUED', '2026-10-06T00:00:00Z', '2026-10-06T00:00:00Z')
                    """);
            try {
                Flyway flyway = legacyMigration();

                // when
                flyway.migrate();

                // then
                try (ResultSet rows = statement
                        .executeQuery("SELECT * FROM knot_test_job_legacy.document_generation_jobs WHERE id = 1")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("status")).isEqualTo("QUEUED");
                    assertThat(
                            rows.getTimestamp("created_at")
                                    .toInstant()
                                    .toString()
                    ).isEqualTo("2026-10-06T00:00:00Z");
                    assertThat(rows.getTimestamp("last_failed_at")).isNull();
                    assertThat(rows.getTimestamp("expires_at")).isNull();
                }
            } finally {
                statement.execute("DROP SCHEMA knot_test_job_legacy CASCADE");
            }
        }
    }

    private Flyway legacyMigration() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("knot_test_job_legacy")
                .defaultSchema("knot_test_job_legacy")
                .baselineOnMigrate(true)
                .baselineVersion("27")
                .target("28")
                .load();
    }
}
