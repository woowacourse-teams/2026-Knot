package com.knot.backend.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.DocumentFixtures;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class DocumentRetentionMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @BeforeEach
    void setUp() {
        flyway("33").clean();
        flyway("33").migrate();
    }

    @Test
    @DisplayName("기존 실패 Job·Batch·완료 오디오를 변경하지 않고 V34~V36을 추가한다")
    void migrate_successPreservesExistingData() throws Exception {
        // given
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        )) {
            JdbcClient jdbc = JdbcClient.create(
                    new SingleConnectionDataSource(
                            connection,
                            true
                    )
            );
            DocumentFixtures fixtures = new DocumentFixtures(jdbc);
            long member = fixtures.saveMember("녹음자");
            long workspace = fixtures.saveWorkspace();
            long recording = fixtures.saveRecording(
                    workspace,
                    member,
                    120000
            );
            long transcript = fixtures.saveTranscript(recording);
            fixtures.saveJob(
                    transcript,
                    "FAILED"
            );
            jdbc.sql("""
                    INSERT INTO recording_audio_uploads
                        (recording_id, storage_key, content_type, content_length, status, reserved_at, completed_at)
                    VALUES (:id, 'recordings/key', 'audio/webm', 100, 'COMPLETED', :time, :time)
                    """)
                    .param(
                            "id",
                            recording
                    )
                    .param(
                            "time",
                            Timestamp.from(DocumentFixtures.CREATED_AT)
                    )
                    .update();
            String before = jdbc.sql("SELECT row_to_json(j)::text FROM document_generation_jobs j")
                    .query(String.class)
                    .single();

            // when
            assertThat(flyway("36").migrate().migrationsExecuted).isEqualTo(3);

            // then
            assertThat(
                    jdbc.sql("SELECT row_to_json(j)::text FROM document_generation_jobs j")
                            .query(String.class)
                            .single()
            ).isEqualTo(before);
            assertThat(
                    jdbc.sql("SELECT processing_status FROM document_generation_batches")
                            .query(String.class)
                            .single()
            ).isEqualTo("FAILED");
            assertThat(
                    jdbc.sql("SELECT failed_count FROM document_generation_batches")
                            .query(Integer.class)
                            .single()
            ).isEqualTo(1);
            assertThat(
                    jdbc.sql(
                            "SELECT count(*) FROM recording_audio_uploads WHERE completed_at IS NOT NULL AND deleted_at IS NULL"
                    )
                            .query(Integer.class)
                            .single()
            ).isEqualTo(1);
            assertThat(
                    jdbc.sql(
                            "SELECT count(*) FROM document_generation_batches WHERE input_released_at IS NULL AND transcript_id IS NOT NULL"
                    )
                            .query(Integer.class)
                            .single()
            ).isEqualTo(1);
            assertThat(
                    jdbc.sql("SELECT count(*) FROM recording_audio_deletion_tasks")
                            .query(Integer.class)
                            .single()
            ).isZero();
            assertThatThrownBy(
                    () -> jdbc.sql("DELETE FROM transcripts")
                            .update()
            ).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(
                    () -> jdbc.sql("UPDATE document_generation_batches SET released_transcript_id = 1")
                            .update()
            ).isInstanceOf(DataIntegrityViolationException.class)
                    .hasStackTraceContaining("ck_generation_batch_released_input");
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .target(target)
                .load();
    }
}
