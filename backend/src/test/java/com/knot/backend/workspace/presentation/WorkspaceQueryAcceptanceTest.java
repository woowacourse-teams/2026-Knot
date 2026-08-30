package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
class WorkspaceQueryAcceptanceTest {
    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final Instant CREATED_AT = Instant.parse("2026-08-29T00:00:00Z");
    private static final Instant JOINED_AT = Instant.parse("2026-08-29T00:01:00Z");

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;

    WorkspaceQueryAcceptanceTest(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            JdbcClient jdbcClient
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.jdbcClient = jdbcClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient
                .sql("TRUNCATE TABLE workspace_members, workspaces, oauth_identities, members RESTART IDENTITY CASCADE")
                .update();
    }

    @Test
    @DisplayName("워크스페이스 멤버가 단건 조회하면 이름을 반환하고 데이터를 변경하지 않는다")
    void detail_success() throws Exception {
        // given
        long memberId = saveMember();
        long workspaceId = saveWorkspace("Knot 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId
        );
        String token = accessToken(memberId);
        String workspaceSnapshot = workspaceSnapshot(workspaceId);
        String workspaceMemberSnapshot = workspaceMemberSnapshot(
                workspaceId,
                memberId
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}",
                        workspaceId
                ).cookie(
                        new Cookie(
                                JWT_COOKIE_NAME,
                                token
                        )
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Knot 팀"));
        assertThat(workspaceSnapshot(workspaceId)).isEqualTo(workspaceSnapshot);
        assertThat(
                workspaceMemberSnapshot(
                        workspaceId,
                        memberId
                )
        ).isEqualTo(workspaceMemberSnapshot);
    }

    @Test
    @DisplayName("워크스페이스 ID가 양수가 아니면 400을 반환한다")
    void detail_failure_invalidWorkspaceId() throws Exception {
        // given
        long memberId = saveMember();
        String token = accessToken(memberId);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}",
                        0
                ).cookie(
                        new Cookie(
                                JWT_COOKIE_NAME,
                                token
                        )
                )
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"))
                .andExpect(jsonPath("$.message").value("워크스페이스 ID가 올바르지 않습니다"));
    }

    @Test
    @DisplayName("인증되지 않은 워크스페이스 단건 조회 요청은 401을 반환한다")
    void detail_failure_unauthenticated() throws Exception {
        // given
        long workspaceId = saveWorkspace("Knot 팀");

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}",
                        workspaceId
                )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다"));
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 403을 반환한다")
    void detail_failure_accessDenied() throws Exception {
        // given
        long memberId = saveMember();
        long workspaceId = saveWorkspace("Knot 팀");
        long otherWorkspaceId = saveWorkspace("다른 팀");
        saveWorkspaceMember(
                otherWorkspaceId,
                memberId
        );
        String token = accessToken(memberId);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}",
                        workspaceId
                ).cookie(
                        new Cookie(
                                JWT_COOKIE_NAME,
                                token
                        )
                )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("워크스페이스에 접근할 수 없습니다"));
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스는 404를 반환한다")
    void detail_failure_workspaceNotFound() throws Exception {
        // given
        long memberId = saveMember();
        String token = accessToken(memberId);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}",
                        Long.MAX_VALUE
                ).cookie(
                        new Cookie(
                                JWT_COOKIE_NAME,
                                token
                        )
                )
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("워크스페이스를 찾을 수 없습니다"));
    }

    private long saveMember() {
        return jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES (:nickname, NULL)
                RETURNING id
                """)
                .param(
                        "nickname",
                        "hyunsung"
                )
                .query(Long.class)
                .single();
    }

    private long saveWorkspace(String name) {
        return jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES (:name, CAST(:createdAt AS TIMESTAMPTZ))
                RETURNING id
                """)
                .param(
                        "name",
                        name
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private void saveWorkspaceMember(
            long workspaceId,
            long memberId
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, 'MEMBER', CAST(:joinedAt AS TIMESTAMPTZ))
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "joinedAt",
                        JOINED_AT.toString()
                )
                .update();
    }

    private String workspaceSnapshot(long workspaceId) {
        return jdbcClient.sql("""
                SELECT name || '|' || created_at::text
                FROM workspaces
                WHERE id = :workspaceId
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(String.class)
                .single();
    }

    private String workspaceMemberSnapshot(
            long workspaceId,
            long memberId
    ) {
        return jdbcClient.sql("""
                SELECT role || '|' || joined_at::text
                FROM workspace_members
                WHERE workspace_id = :workspaceId AND member_id = :memberId
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .query(String.class)
                .single();
    }

    private String accessToken(long memberId) {
        return authTokenProvider.issue(
                AuthenticatedMember.of(
                        memberId,
                        "hyunsung",
                        null
                )
        );
    }

}
