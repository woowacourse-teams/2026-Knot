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
class WorkspaceMemberInvitationSourceMigrationIntegrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("pgvector/pgvector:pg18")
            .withDatabaseName("membership_source_migration")
            .withUsername("knot")
            .withPassword("knot");

    @BeforeEach
    void setUp() throws SQLException {
        flyway("19").clean();
        flyway("19").migrate();
        execute("""
                INSERT INTO members (id, nickname) OVERRIDING SYSTEM VALUE
                VALUES (1, '생성자'), (2, '기존회원'), (3, '새회원');
                INSERT INTO workspaces (id, name, created_at)
                VALUES (1, '첫 팀', '2026-10-03T00:00Z'), (2, '다른 팀', '2026-10-03T00:00Z');
                INSERT INTO workspace_invitations
                    (id, workspace_id, link_token_hash, invite_code_hash, created_at, expires_at)
                VALUES (100, 1, 'link-1', 'code-1', '2026-10-03T00:00Z', '2026-10-04T00:00Z'),
                       (101, 2, 'link-2', 'code-2', '2026-10-03T00:00Z', '2026-10-04T00:00Z');
                INSERT INTO workspace_members
                    (id, workspace_id, member_id, role, joined_at, left_at, last_viewed)
                VALUES (1, 1, 1, 'OWNER', '2026-10-03T00:00Z', NULL, TRUE),
                       (2, 1, 2, 'MEMBER', '2026-10-03T00:01Z', '2026-10-03T00:02Z', FALSE),
                       (3, 1, 2, 'MEMBER', '2026-10-03T00:03Z', NULL, FALSE);
                """);
    }

    @Test
    @DisplayName("V20은 기존 역할·가입·탈퇴·마지막 조회를 보존하고 출처는 NULL로 남긴다")
    void migrate_preservesExistingMemberships() throws SQLException {
        // given
        List<String> before = legacyRows();

        // when
        MigrateResult result = flyway("20").migrate();

        // then
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(legacyRows()).isEqualTo(before);
        assertThat(query("SELECT count(*)::text FROM workspace_members WHERE source_invitation_id IS NULL"))
                .containsExactly("3");
    }

    @Test
    @DisplayName("구 INSERT는 새 컬럼을 생략해도 출처 NULL로 저장한다")
    void insert_acceptsLegacyWriter() throws SQLException {
        // given
        flyway("20").migrate();

        // when
        execute("""
                INSERT INTO workspace_members (id, workspace_id, member_id, role, joined_at)
                VALUES (4, 1, 3, 'MEMBER', '2026-10-03T00:04Z')
                """);

        // then
        assertThat(query("SELECT (source_invitation_id IS NULL)::text FROM workspace_members WHERE id = 4"))
                .containsExactly("true");
    }

    @ParameterizedTest
    @ValueSource(longs = {101, 999})
    @DisplayName("DB는 다른 Workspace 또는 미존재 초대 출처를 거부한다")
    void update_rejectsInvalidInvitationReference(long invitationId) throws SQLException {
        // given
        flyway("20").migrate();
        List<String> before = allRows();

        // when
        ThrowingCallable action = () -> execute(
                "UPDATE workspace_members SET source_invitation_id = " + invitationId + " WHERE id = 3"
        );

        // then
        assertThatThrownBy(action).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23503"));
        assertThat(allRows()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("DB는 양수가 아닌 출처 ID를 거부한다")
    void update_rejectsNonPositiveSource(long invitationId) throws SQLException {
        // given
        flyway("20").migrate();

        // when
        ThrowingCallable action = () -> execute(
                "UPDATE workspace_members SET source_invitation_id = " + invitationId + " WHERE id = 3"
        );

        // then
        assertThatThrownBy(action).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    @Test
    @DisplayName("탈퇴 이력이 참조하는 초대도 삭제할 수 없으며 출처를 보존한다")
    void delete_restrictsInvitationReferencedByHistory() throws SQLException {
        // given
        flyway("20").migrate();
        execute("UPDATE workspace_members SET source_invitation_id = 100 WHERE id = 2");
        List<String> before = allRows();

        // when
        ThrowingCallable action = () -> execute("DELETE FROM workspace_invitations WHERE id = 100");

        // then
        assertThatThrownBy(action).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23001"));
        assertThat(allRows()).isEqualTo(before);
        assertThat(query("SELECT count(*)::text FROM workspace_invitations WHERE id = 100")).containsExactly("1");
    }

    private List<String> legacyRows() throws SQLException {
        return query("""
                SELECT row_to_json(membership)::text FROM (
                    SELECT id, workspace_id, member_id, role, joined_at, left_at, last_viewed
                    FROM workspace_members ORDER BY id
                ) membership
                """);
    }

    private List<String> allRows() throws SQLException {
        return query("SELECT row_to_json(membership)::text FROM workspace_members membership ORDER BY id");
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
