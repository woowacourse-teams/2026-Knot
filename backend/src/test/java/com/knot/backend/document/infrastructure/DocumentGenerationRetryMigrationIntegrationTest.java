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
class DocumentGenerationRetryMigrationIntegrationTest {

    private static final String SCHEMA = "knot_test_retry_legacy";

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("시도 이력을 모르는 기존 Job은 추정하지 않고 이행을 중단·보존한다")
    void migrate_failure_unverifiedCounts() throws Exception {
        // given
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            createLegacyTable(statement);
            statement.execute("INSERT INTO knot_test_retry_legacy.document_generation_jobs (id) VALUES (1)");
            try {
                // when & then
                assertThatThrownBy(() -> migration().migrate()).isInstanceOf(FlywayException.class)
                        .hasStackTraceContaining("verified attempt counters");
                try (ResultSet rows = statement
                        .executeQuery("SELECT id FROM knot_test_retry_legacy.document_generation_jobs")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("id")).isEqualTo(1);
                }
            } finally {
                statement.execute("DROP SCHEMA knot_test_retry_legacy CASCADE");
            }
        }
    }

    @Test
    @DisplayName("운영 기록으로 확인해 미리 채운 횟수는 초기화하지 않고 이행한다")
    void migrate_success_verifiedCounts() throws Exception {
        // given
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            createLegacyTable(statement);
            statement.execute("""
                    ALTER TABLE knot_test_retry_legacy.document_generation_jobs
                        ADD COLUMN attempt_count INTEGER,
                        ADD COLUMN user_retry_count INTEGER,
                        ADD COLUMN automatic_retry_count INTEGER;
                    INSERT INTO knot_test_retry_legacy.document_generation_jobs VALUES (1, 7, 1, 5)
                    """);
            try {
                // when
                migration().migrate();

                // then
                try (ResultSet rows = statement
                        .executeQuery("SELECT * FROM knot_test_retry_legacy.document_generation_jobs")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt("attempt_count")).isEqualTo(7);
                    assertThat(rows.getInt("user_retry_count")).isEqualTo(1);
                    assertThat(rows.getInt("automatic_retry_count")).isEqualTo(5);
                }
                statement.execute("INSERT INTO knot_test_retry_legacy.document_generation_jobs (id) VALUES (2)");
                try (ResultSet rows = statement
                        .executeQuery("SELECT * FROM knot_test_retry_legacy.document_generation_jobs WHERE id = 2")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt("attempt_count")).isEqualTo(1);
                    assertThat(rows.getInt("user_retry_count")).isZero();
                    assertThat(rows.getInt("automatic_retry_count")).isZero();
                }
            } finally {
                statement.execute("DROP SCHEMA knot_test_retry_legacy CASCADE");
            }
        }
    }

    private void createLegacyTable(Statement statement) throws Exception {
        statement.execute("""
                CREATE SCHEMA knot_test_retry_legacy;
                CREATE TABLE knot_test_retry_legacy.document_generation_jobs (id BIGINT PRIMARY KEY)
                """);
    }

    private Flyway migration() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .baselineOnMigrate(true)
                .baselineVersion("29")
                .target("30")
                .load();
    }
}
