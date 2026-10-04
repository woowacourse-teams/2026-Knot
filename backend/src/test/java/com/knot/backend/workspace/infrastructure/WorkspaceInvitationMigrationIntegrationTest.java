package com.knot.backend.workspace.infrastructure;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class WorkspaceInvitationMigrationIntegrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("pgvector/pgvector:pg18")
            .withDatabaseName("invitation_migration")
            .withUsername("knot")
            .withPassword("knot");

    @BeforeEach
    void setUp() {
        flyway("17").clean();
        flyway("17").migrate();
    }

    @Test
    @DisplayName("FK migration은 유효·만료·무효 초대의 공통 만료와 기존 데이터를 보존한다")
    void migrate_preservesLegacyRows() throws SQLException {
        // given
        execute("""
                INSERT INTO workspaces (id, name, created_at)
                VALUES (1, '유효', CURRENT_TIMESTAMP), (2, '만료', CURRENT_TIMESTAMP), (3, '무효', CURRENT_TIMESTAMP);
                INSERT INTO workspace_invitations
                    (workspace_id, link_token_hash, invite_code_hash, created_at, expires_at, invalidated_at)
                VALUES
                    (1, 'link-1', 'code-1', '2026-10-03T00:00:00Z', '2026-10-04T00:00:00Z', NULL),
                    (2, 'link-2', 'code-2', '2026-10-01T00:00:00Z', '2026-10-02T00:00:00Z', NULL),
                    (3, 'link-3', 'code-3', '2026-10-03T00:00:00Z', '2026-10-04T00:00:00Z', '2026-10-03 01:00Z');
                """);
        List<String> before = legacyRows();

        // when
        MigrateResult result = flyway("19").migrate();

        // then
        assertThat(result.success).isTrue();
        assertThat(legacyRows()).isEqualTo(before);
        assertThat(query("""
                SELECT (invalidated_at IS NULL AND '2026-10-03 12:00Z'::timestamptz >= created_at
                    AND '2026-10-03 12:00Z'::timestamptz < expires_at)::text
                FROM workspace_invitations ORDER BY id
                """)).containsExactly(
                "true",
                "false",
                "false"
        );
    }

    @Test
    @DisplayName("FK migration 뒤 기존 INSERT는 공통 24시간 만료 초대를 저장한다")
    void insert_acceptsLegacyWriter() throws SQLException {
        // given
        flyway("19").migrate();
        execute("INSERT INTO workspaces (id, name, created_at) VALUES (1, '호환 팀', CURRENT_TIMESTAMP)");

        // when
        insertLegacyInvitation();

        // then
        assertThat(query("""
                SELECT (expires_at = created_at + INTERVAL '24 hours')::text
                FROM workspace_invitations
                """)).containsExactly("true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"created_at", "created_at - INTERVAL '1 second'", "created_at + INTERVAL '23 hours'"})
    @DisplayName("DB는 공통 만료가 생성 시각의 24시간 뒤가 아니면 거부한다")
    void update_rejectsInvalidExpirations(String expiration) throws SQLException {
        // given
        flyway("19").migrate();
        execute("INSERT INTO workspaces (id, name, created_at) VALUES (1, '제약 팀', CURRENT_TIMESTAMP)");
        insertLegacyInvitation();

        // when
        ThrowingCallable action = () -> execute("UPDATE workspace_invitations SET expires_at = " + expiration);

        // then
        assertThatThrownBy(action).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    @Test
    @DisplayName("초대가 있으면 Workspace 물리 삭제를 거부하고 이력을 보존한다")
    void delete_restrictsWorkspaceWithInvitation() throws SQLException {
        // given
        flyway("19").migrate();
        execute("INSERT INTO workspaces (id, name, created_at) VALUES (1, '보존 팀', CURRENT_TIMESTAMP)");
        insertLegacyInvitation();
        List<String> before = legacyRows();

        // when
        ThrowingCallable action = () -> execute("DELETE FROM workspaces WHERE id = 1");

        // then
        assertThatThrownBy(action).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23001"));
        assertThat(legacyRows()).isEqualTo(before);
        assertThat(query("SELECT count(*)::text FROM workspaces")).containsExactly("1");
    }

    private void insertLegacyInvitation() throws SQLException {
        execute("""
                INSERT INTO workspace_invitations
                    (workspace_id, link_token_hash, invite_code_hash, created_at, expires_at)
                VALUES (1, 'legacy-link', 'legacy-code', '2026-10-03T00:00:00Z', '2026-10-04T00:00:00Z')
                """);
    }

    private List<String> legacyRows() throws SQLException {
        return query("""
                SELECT row_to_json(legacy)::text FROM (
                    SELECT id, workspace_id, link_token_hash, invite_code_hash,
                        link_token_ciphertext, invite_code_ciphertext, created_at, expires_at, invalidated_at, version
                    FROM workspace_invitations ORDER BY id
                ) legacy
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
