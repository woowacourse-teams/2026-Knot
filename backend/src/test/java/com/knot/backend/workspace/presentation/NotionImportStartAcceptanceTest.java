package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotionImportStartAcceptanceTest {
    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;

    NotionImportStartAcceptanceTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            AuthTokenProvider authTokenProvider,
            JdbcClient jdbcClient
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.authTokenProvider = authTokenProvider;
        this.jdbcClient = jdbcClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient.sql("""
                TRUNCATE TABLE notion_pages, notion_import_runs, content_source_connections,
                    content_source_authorizations, workspace_members, workspaces, oauth_identities, members
                RESTART IDENTITY CASCADE
                """)
                .update();
    }

    @DisplayName("현재 OWNER가 CONNECTED Workspace의 Import를 시작하면 202와 PENDING Run을 반환한다")
    @Test
    void start_success_createdPendingRun() throws Exception {
        // given
        TestContext context = saveConnectedOwnerContext("owner");
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        context.workspaceId()
                ).cookie(
                        accessTokenCookie(
                                context.memberId(),
                                "owner"
                        ),
                        csrfCredentials.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );

        // then
        long importRunId = singleImportRunId();
        result.andExpect(status().isAccepted())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                "/api/v1/imports/" + importRunId
                        )
                )
                .andExpect(jsonPath("$.id").value(importRunId));
        assertThat(importRunSnapshot(importRunId)).isEqualTo(
                "%d|%d|%d|PENDING|null|0|null|null".formatted(
                        context.workspaceId(),
                        context.connectionId(),
                        context.memberId()
                )
        );
    }

    @DisplayName("활성 Run이 있으면 새 Row 없이 같은 ID와 Location을 포함한 409를 반환한다")
    @ValueSource(strings = {"PENDING", "RUNNING"})
    @ParameterizedTest(name = "{0}")
    void start_success_existingActiveRun(String statusValue) throws Exception {
        // given
        TestContext context = saveConnectedOwnerContext("owner");
        long importRunId = saveActiveImportRun(
                context,
                statusValue
        );
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        context.workspaceId()
                ).cookie(
                        accessTokenCookie(
                                context.memberId(),
                                "owner"
                        ),
                        csrfCredentials.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                "/api/v1/imports/" + importRunId
                        )
                )
                .andExpect(jsonPath("$.id").value(importRunId));
        assertThat(importRunCount()).isOne();
    }

    @DisplayName("Workspace ID가 양수가 아니면 400을 반환하고 Run을 만들지 않는다")
    @Test
    void start_failure_invalidWorkspaceId() throws Exception {
        // given
        long memberId = saveMember("owner");
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        0
                ).cookie(
                        accessTokenCookie(
                                memberId,
                                "owner"
                        ),
                        csrfCredentials.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"));
        assertThat(importRunCount()).isZero();
    }

    @DisplayName("인증되지 않은 Import 시작 요청은 401을 반환하고 Run을 만들지 않는다")
    @Test
    void start_failure_unauthenticated() throws Exception {
        // given
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        1
                ).cookie(csrfCredentials.cookie())
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(importRunCount()).isZero();
    }

    @DisplayName("CSRF 토큰이 없는 Import 시작 요청은 403을 반환하고 Run을 만들지 않는다")
    @Test
    void start_failure_missingCsrf() throws Exception {
        // given
        TestContext context = saveConnectedOwnerContext("owner");

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        context.workspaceId()
                ).cookie(
                        accessTokenCookie(
                                context.memberId(),
                                "owner"
                        )
                )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(importRunCount()).isZero();
    }

    @DisplayName("현재 MEMBER의 Import 시작 요청은 403을 반환하고 Run을 만들지 않는다")
    @Test
    void start_failure_ownerRequired() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("Knot 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );
        saveConnection(
                workspaceId,
                ownerId
        );
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                memberId,
                                "member"
                        ),
                        csrfCredentials.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_OWNER_REQUIRED"));
        assertThat(importRunCount()).isZero();
    }

    @DisplayName("존재하지 않는 Workspace의 Import 시작 요청은 404를 반환하고 Run을 만들지 않는다")
    @Test
    void start_failure_workspaceNotFound() throws Exception {
        // given
        long memberId = saveMember("owner");
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        Long.MAX_VALUE
                ).cookie(
                        accessTokenCookie(
                                memberId,
                                "owner"
                        ),
                        csrfCredentials.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
        assertThat(importRunCount()).isZero();
    }

    @DisplayName("Notion Connection이 없으면 409를 반환하고 Run을 만들지 않는다")
    @Test
    void start_failure_notionConnectionNotConnected() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace("Knot 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                ownerId,
                                "owner"
                        ),
                        csrfCredentials.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTION_CONNECTION_NOT_CONNECTED"));
        assertThat(importRunCount()).isZero();
    }

    @DisplayName("Connection 승인자가 현재 OWNER가 아니면 409 재인증 오류를 반환한다")
    @Test
    void start_failure_notionConnectionReauthenticationRequired() throws Exception {
        // given
        long currentOwnerId = saveMember("current-owner");
        long formerOwnerId = saveMember("former-owner");
        long workspaceId = saveWorkspace("Knot 팀");
        saveWorkspaceMember(
                workspaceId,
                currentOwnerId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                formerOwnerId,
                "MEMBER"
        );
        saveConnection(
                workspaceId,
                formerOwnerId
        );
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                currentOwnerId,
                                "current-owner"
                        ),
                        csrfCredentials.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTION_CONNECTION_REAUTHENTICATION_REQUIRED"));
        assertThat(importRunCount()).isZero();
    }

    private TestContext saveConnectedOwnerContext(String nickname) {
        long memberId = saveMember(nickname);
        long workspaceId = saveWorkspace(nickname + " 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );
        long connectionId = saveConnection(
                workspaceId,
                memberId
        );
        return new TestContext(
                memberId,
                workspaceId,
                connectionId
        );
    }

    private long saveMember(String nickname) {
        return jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES (:nickname, NULL)
                RETURNING id
                """)
                .param(
                        "nickname",
                        nickname
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
            long memberId,
            String role
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, :role, CAST(:joinedAt AS TIMESTAMPTZ))
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
                        "role",
                        role
                )
                .param(
                        "joinedAt",
                        CREATED_AT.toString()
                )
                .update();
    }

    private long saveConnection(
            long workspaceId,
            long authorizingMemberId
    ) {
        return jdbcClient.sql("""
                INSERT INTO content_source_connections (
                    workspace_id,
                    provider,
                    access_credential_ciphertext,
                    external_source_id,
                    provider_connection_id,
                    authorization_owner_type,
                    authorizing_member_id,
                    created_at,
                    updated_at,
                    version
                ) VALUES (
                    :workspaceId,
                    'NOTION',
                    'encrypted-access-token',
                    :externalSourceId,
                    :providerConnectionId,
                    'WORKSPACE',
                    :authorizingMemberId,
                    CAST(:createdAt AS TIMESTAMPTZ),
                    CAST(:createdAt AS TIMESTAMPTZ),
                    0
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "externalSourceId",
                        "notion-workspace-" + workspaceId
                )
                .param(
                        "providerConnectionId",
                        "notion-bot-" + workspaceId
                )
                .param(
                        "authorizingMemberId",
                        authorizingMemberId
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private long saveActiveImportRun(
            TestContext context,
            String status
    ) {
        String startedAt = status.equals("RUNNING")
                ? CREATED_AT.plusSeconds(1)
                        .toString()
                : null;
        return jdbcClient.sql("""
                INSERT INTO notion_import_runs (
                    workspace_id,
                    content_source_connection_id,
                    requested_by_member_id,
                    status,
                    total_page_count,
                    processed_page_count,
                    started_at,
                    completed_at,
                    created_at
                ) VALUES (
                    :workspaceId,
                    :connectionId,
                    :memberId,
                    :status,
                    NULL,
                    0,
                    CAST(:startedAt AS TIMESTAMPTZ),
                    NULL,
                    CAST(:createdAt AS TIMESTAMPTZ)
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        context.workspaceId()
                )
                .param(
                        "connectionId",
                        context.connectionId()
                )
                .param(
                        "memberId",
                        context.memberId()
                )
                .param(
                        "status",
                        status
                )
                .param(
                        "startedAt",
                        startedAt
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private String importRunSnapshot(long importRunId) {
        return jdbcClient.sql("""
                SELECT CONCAT_WS(
                    '|',
                    workspace_id,
                    content_source_connection_id,
                    requested_by_member_id,
                    status,
                    COALESCE(total_page_count::text, 'null'),
                    processed_page_count,
                    COALESCE(started_at::text, 'null'),
                    COALESCE(completed_at::text, 'null')
                )
                FROM notion_import_runs
                WHERE id = :importRunId
                """)
                .param(
                        "importRunId",
                        importRunId
                )
                .query(String.class)
                .single();
    }

    private long singleImportRunId() {
        return jdbcClient.sql("SELECT id FROM notion_import_runs")
                .query(Long.class)
                .single();
    }

    private long importRunCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM notion_import_runs")
                .query(Long.class)
                .single();
    }

    private Cookie accessTokenCookie(
            long memberId,
            String nickname
    ) {
        String token = authTokenProvider.issue(
                AuthenticatedMember.of(
                        memberId,
                        nickname,
                        null
                )
        );
        return new Cookie(
                JWT_COOKIE_NAME,
                token
        );
    }

    private CsrfCredentials csrfCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);
        assertThat(cookie).isNotNull();
        JsonNode responseBody = objectMapper.readTree(
                result.getResponse()
                        .getContentAsString()
        );
        return new CsrfCredentials(
                cookie,
                responseBody.get("token")
                        .asText()
        );
    }

    private record CsrfCredentials(
            Cookie cookie,
            String token
    ) {
    }

    private record TestContext(
            long memberId,
            long workspaceId,
            long connectionId
    ) {
    }
}
