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
class DocumentGenerationRegistrationMigrationIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("단계와 주제를 확인하지 못한 기존 Job이 있으면 V32를 중단하고 원본 행을 보존한다")
    void migrate_failure_unverifiedLegacyStage() throws Exception {
        // given
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE SCHEMA knot_test_registration_legacy;
                    CREATE TABLE knot_test_registration_legacy.document_generation_jobs (id BIGINT PRIMARY KEY);
                    INSERT INTO knot_test_registration_legacy.document_generation_jobs VALUES (88)
                    """);
            try {
                Flyway flyway = Flyway.configure()
                        .dataSource(dataSource)
                        .schemas("knot_test_registration_legacy")
                        .defaultSchema("knot_test_registration_legacy")
                        .baselineOnMigrate(true)
                        .baselineVersion("31")
                        .target("32")
                        .load();
                // when & then
                assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class)
                        .hasStackTraceContaining("verified legacy job stages and topics");
                try (ResultSet rows = statement
                        .executeQuery("SELECT id FROM knot_test_registration_legacy.document_generation_jobs")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("id")).isEqualTo(88);
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                statement.execute("DROP SCHEMA knot_test_registration_legacy CASCADE");
            }
        }
    }

    @Test
    @DisplayName("빈 Job 테이블은 V32까지 적용되어 등록 결과와 주제 FK 제약을 제공한다")
    void migrate_success_emptyJobTable() throws Exception {
        // when
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT count(*) FROM pg_constraint
                        WHERE conname IN ('uk_generation_job_topic', 'fk_generation_job_topic',
                            'fk_generation_job_batch_input', 'ck_generation_batch_state')
                        """)) {
            // then
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(4);
        }
    }
}
