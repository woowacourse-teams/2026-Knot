package com.knot.backend.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.DocumentFixtures;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class DocumentGenerationExecutionMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @BeforeEach
    void setUp() {
        flyway("32").clean();
        flyway("32").migrate();
    }

    @Test
    @DisplayName("기존 RUNNING을 회수 가능하게 이행하고 분류 진행 상태를 보존한다")
    void migrate_success_recoversLegacyRunning() throws Exception {
        // given
        try (Connection connection = connection()) {
            JdbcClient jdbc = JdbcClient.create(
                    new SingleConnectionDataSource(
                            connection,
                            true
                    )
            );
            seedClassification(
                    jdbc,
                    "RUNNING"
            );
            // when
            assertThat(flyway("33").migrate().migrationsExecuted).isEqualTo(1);
            // then
            assertThat(
                    jdbc.sql("SELECT execution_deadline_at = updated_at FROM document_generation_jobs")
                            .query(Boolean.class)
                            .single()
            ).isTrue();
            assertThat(
                    jdbc.sql("SELECT processing_status FROM document_generation_batches")
                            .query(String.class)
                            .single()
            ).isEqualTo("RUNNING");
            assertThat(
                    jdbc.sql(
                            "SELECT queued_count + running_count + succeeded_count + failed_count FROM document_generation_batches"
                    )
                            .query(Integer.class)
                            .single()
            ).isZero();
        }
    }

    @Test
    @DisplayName("분류 성공만 있고 동결 주제의 생성 작업이 누락됐다면 이행을 중단하고 기존 행을 보존한다")
    void migrate_failure_missingGenerationJob() throws Exception {
        // given
        try (Connection connection = connection()) {
            JdbcClient jdbc = JdbcClient.create(
                    new SingleConnectionDataSource(
                            connection,
                            true
                    )
            );
            seedClassification(
                    jdbc,
                    "SUCCEEDED"
            );
            jdbc.sql(
                    "UPDATE document_generation_batches SET topic_registration_state = 'TOPICS_REGISTERED', registered_at = accepted_at"
            )
                    .update();
            jdbc.sql("INSERT INTO document_generation_batch_topics VALUES (1, 0, '누락 주제')")
                    .update();
            // when & then
            assertThatThrownBy(() -> flyway("33").migrate()).isInstanceOf(FlywayException.class)
                    .hasStackTraceContaining("verified batch progress");
            assertThat(
                    jdbc.sql("SELECT status FROM document_generation_jobs")
                            .query(String.class)
                            .single()
            ).isEqualTo("SUCCEEDED");
            assertThat(
                    jdbc.sql(
                            "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='document_generation_jobs' AND column_name='execution_deadline_at'"
                    )
                            .query(Long.class)
                            .single()
            ).isZero();
        }
    }

    private void seedClassification(
            JdbcClient jdbc,
            String status
    ) {
        DocumentFixtures fixtures = new DocumentFixtures(jdbc);
        long member = fixtures.saveMember("테스트");
        long workspace = fixtures.saveWorkspace();
        long recording = fixtures.saveRecording(
                workspace,
                member,
                1000
        );
        long transcript = fixtures.saveTranscript(recording);
        jdbc.sql(
                """
                        INSERT INTO document_generation_batches (recording_session_id, transcript_id, topic_registration_state, accepted_at)
                        VALUES (:recording, :transcript, 'WAITING_CLASSIFICATION', :now)
                        """
        )
                .param(
                        "recording",
                        recording
                )
                .param(
                        "transcript",
                        transcript
                )
                .param(
                        "now",
                        Timestamp.from(DocumentFixtures.CREATED_AT)
                )
                .update();
        jdbc.sql("""
                INSERT INTO document_generation_jobs (batch_id, transcript_id, stage, status, created_at, updated_at)
                VALUES (1, :transcript, 'CLASSIFICATION', :status, :now, :now)
                """)
                .param(
                        "transcript",
                        transcript
                )
                .param(
                        "status",
                        status
                )
                .param(
                        "now",
                        Timestamp.from(DocumentFixtures.CREATED_AT)
                )
                .update();
    }

    @Test
    @DisplayName("기존 주제별 진행·성공 문서를 대조하여 집계를 보존한다")
    void migrate_success_preservesGenerationCounts() throws Exception {
        // given
        try (Connection connection = connection()) {
            JdbcClient jdbc = JdbcClient.create(
                    new SingleConnectionDataSource(
                            connection,
                            true
                    )
            );
            seedClassification(
                    jdbc,
                    "SUCCEEDED"
            );
            jdbc.sql(
                    "UPDATE document_generation_batches SET topic_registration_state = 'TOPICS_REGISTERED', registered_at = accepted_at"
            )
                    .update();
            jdbc.sql("INSERT INTO document_generation_batch_topics VALUES (1,0,'A'),(1,1,'B'),(1,2,'C')")
                    .update();
            jdbc.sql(
                    """
                            INSERT INTO document_generation_jobs (batch_id,transcript_id,stage,topic,status,created_at,updated_at)
                            VALUES (1,1,'GENERATION','A','QUEUED',:now,:now),
                                (1,1,'GENERATION','B','RUNNING',:now,:now),
                                (1,1,'GENERATION','C','SUCCEEDED',:now,:now)
                            """
            )
                    .param(
                            "now",
                            Timestamp.from(DocumentFixtures.CREATED_AT)
                    )
                    .update();
            new DocumentFixtures(jdbc).saveDocument(
                    1,
                    1,
                    1,
                    4,
                    "C"
            );
            // when
            flyway("33").migrate();
            // then
            assertThat(
                    jdbc.sql("SELECT processing_status FROM document_generation_batches")
                            .query(String.class)
                            .single()
            ).isEqualTo("RUNNING");
            assertThat(
                    jdbc.sql(
                            "SELECT queued_count || ',' || running_count || ',' || succeeded_count || ',' || failed_count FROM document_generation_batches"
                    )
                            .query(String.class)
                            .single()
            ).isEqualTo("1,1,1,0");
            assertThat(
                    jdbc.sql("SELECT count(*) FROM documents")
                            .query(Long.class)
                            .single()
            ).isEqualTo(1);
            assertThat(
                    jdbc.sql("SELECT next_attempt_at = updated_at FROM document_generation_jobs WHERE status='QUEUED'")
                            .query(Boolean.class)
                            .single()
            ).isTrue();
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
                .target(target)
                .cleanDisabled(false)
                .load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        );
    }
}
