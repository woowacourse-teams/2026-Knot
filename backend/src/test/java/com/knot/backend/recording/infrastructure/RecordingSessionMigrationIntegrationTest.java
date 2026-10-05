package com.knot.backend.recording.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class RecordingSessionMigrationIntegrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("pgvector/pgvector:pg18")
            .withDatabaseName("recording_migration")
            .withUsername("knot")
            .withPassword("knot");

    @BeforeEach
    void setUp() throws SQLException {
        flyway("20").clean();
        flyway("20").migrate();
        execute("""
                INSERT INTO members (id, nickname) OVERRIDING SYSTEM VALUE
                VALUES (1, 'recording'), (2, 'paused'), (3, 'ended');
                INSERT INTO workspaces (id, name, created_at) OVERRIDING SYSTEM VALUE
                VALUES (1, '녹음 팀', '2026-10-01T00:00:00Z');
                """);
    }

    @Test
    @DisplayName("폐기 상태 migration은 기존 녹음 행을 보존하고 DISCARDED를 활성 제약 밖에서 허용한다")
    void migrate_preservesRecordingsAndAllowsDiscarded() throws SQLException {
        // given
        execute("""
                INSERT INTO recording_sessions (
                    workspace_id, member_id, request_id, tab_id, control_token_hash, status,
                    started_at, current_interval_started_at, ended_at, last_seen_at
                )
                VALUES
                    (1, 1, gen_random_uuid(), gen_random_uuid(), repeat('a', 64), 'RECORDING',
                        '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z', NULL, '2026-10-01T00:00:00Z'),
                    (1, 2, gen_random_uuid(), gen_random_uuid(), repeat('b', 64), 'PAUSED',
                        '2026-10-01T00:00:00Z', NULL, NULL, '2026-10-01T00:00:10Z'),
                    (1, 3, gen_random_uuid(), gen_random_uuid(), repeat('c', 64), 'ENDED',
                        '2026-10-01T00:00:00Z', NULL, '2026-10-01T00:00:20Z', '2026-10-01T00:00:20Z');
                """);
        List<String> before = recordingRows();

        // when
        MigrateResult result = flyway("23").migrate();

        // then
        assertThat(result.success).isTrue();
        assertThat(recordingRows()).isEqualTo(before);
        execute("""
                UPDATE recording_sessions
                SET status = 'DISCARDED', current_interval_started_at = NULL,
                    ended_at = '2026-10-01T00:00:30Z', last_seen_at = '2026-10-01T00:00:30Z'
                WHERE member_id = 1;
                INSERT INTO recording_sessions (
                    workspace_id, member_id, request_id, tab_id, control_token_hash, status,
                    started_at, current_interval_started_at, last_seen_at
                )
                VALUES (1, 1, gen_random_uuid(), gen_random_uuid(), repeat('d', 64), 'RECORDING',
                    '2026-10-01T00:01:00Z', '2026-10-01T00:01:00Z', '2026-10-01T00:01:00Z');
                """);
        assertThat(query("""
                SELECT status FROM recording_sessions WHERE member_id = 1 ORDER BY id
                """)).containsExactly(
                "DISCARDED",
                "RECORDING"
        );
    }

    @Test
    @DisplayName("일시정지 시각 migration은 기존 PAUSED 행을 마지막 확인 시각으로 채우고 시작 전 일시정지 시각은 거부한다")
    void migrate_backfillsPausedAtAndRejectsPausedAtBeforeStartedAt() throws SQLException {
        // given
        flyway("23").migrate();
        execute("""
                INSERT INTO recording_sessions (
                    workspace_id, member_id, request_id, tab_id, control_token_hash, status,
                    started_at, last_seen_at
                )
                VALUES (1, 1, gen_random_uuid(), gen_random_uuid(), repeat('a', 64), 'PAUSED',
                    '2026-10-01T00:00:00Z', '2026-10-01T00:00:10Z')
                """);
        flyway("24").migrate();

        // when
        ThrowingCallable action = () -> execute("""
                UPDATE recording_sessions SET paused_at = '2026-09-30T23:59:59Z' WHERE member_id = 1
                """);

        // then
        assertThat(query("""
                SELECT status FROM recording_sessions
                WHERE member_id = 1 AND paused_at = '2026-10-01T00:00:10Z'
                """)).containsExactly("PAUSED");
        assertThatThrownBy(action).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    @Test
    @DisplayName("폐기 상태 migration 뒤에도 중단 시각이 없는 DISCARDED는 거부한다")
    void insert_rejectsDiscardedWithoutEndedAt() {
        // given
        flyway("23").migrate();

        // when
        ThrowingCallable action = () -> execute("""
                INSERT INTO recording_sessions (
                    workspace_id, member_id, request_id, tab_id, control_token_hash, status,
                    started_at, last_seen_at
                )
                VALUES (1, 1, gen_random_uuid(), gen_random_uuid(), repeat('a', 64), 'DISCARDED',
                    '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')
                """);

        // then
        assertThatThrownBy(action).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    private List<String> recordingRows() throws SQLException {
        return query("""
                SELECT row_to_json(recording)::text FROM (
                    SELECT * FROM recording_sessions ORDER BY id
                ) recording
                """);
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

    private void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private List<String> query(String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                rows.add(result.getString(1));
            }
        }
        return rows;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        );
    }
}
