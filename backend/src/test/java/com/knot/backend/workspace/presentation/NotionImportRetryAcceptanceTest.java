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
class NotionImportRetryAcceptanceTest {
    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;

    NotionImportRetryAcceptanceTest(
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

    @DisplayName("현재 OWNER가 FAILED Run을 재시도하면 원본을 보존하고 새 PENDING Run을 반환한다")
    @Test
    void retry_success_createdPendingRun() throws Exception {
        // given
        TestContext context = saveConnectedOwnerContext("owner");
        long originalImportRunId = saveImportRun(
                context,
                "FAILED"
        );
        String originalSnapshot = importRunSnapshot(originalImportRunId);
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = retry(
                originalImportRunId,
                accessTokenCookie(
                        context.memberId(),
                        "owner"
                ),
                csrfCredentials
        );

        // then
        long newImportRunId = activeImportRunId(context.connectionId());
        result.andExpect(status().isAccepted())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                "/api/v1/imports/" + newImportRunId
                        )
                )
                .andExpect(jsonPath("$.id").value(newImportRunId));
        assertThat(newImportRunId).isNotEqualTo(originalImportRunId);
        assertThat(importRunSnapshot(newImportRunId)).isEqualTo(
                "%d|%d|%d|PENDING|null|0|null|null".formatted(
                        context.workspaceId(),
                        context.connectionId(),
                        context.memberId()
                )
        );
        assertThat(importRunSnapshot(originalImportRunId)).isEqualTo(originalSnapshot);
        assertThat(importRunCount()).isEqualTo(2);
    }

    @DisplayName("같은 Workspace에 활성 Run이 있으면 원본을 보존하고 현재 ID와 Location을 포함한 409를 반환한다")
    @ValueSource(strings = {"PENDING", "RUNNING"})
    @ParameterizedTest(name = "{0}")
    void retry_success_existingActiveRun(String activeStatus) throws Exception {
        // given
        TestContext context = saveConnectedOwnerContext("owner");
        long originalImportRunId = saveImportRun(
                context,
                "FAILED"
        );
        long activeImportRunId = saveImportRun(
                context,
                activeStatus
        );
        String originalSnapshot = importRunSnapshot(originalImportRunId);
        String activeSnapshot = importRunSnapshot(activeImportRunId);
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = retry(
                originalImportRunId,
                accessTokenCookie(
                        context.memberId(),
                        "owner"
                ),
                csrfCredentials
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                "/api/v1/imports/" + activeImportRunId
                        )
                )
                .andExpect(jsonPath("$.id").value(activeImportRunId));
        assertThat(importRunSnapshot(originalImportRunId)).isEqualTo(originalSnapshot);
        assertThat(importRunSnapshot(activeImportRunId)).isEqualTo(activeSnapshot);
        assertThat(importRunCount()).isEqualTo(2);
    }

    @DisplayName("FAILED가 아닌 원본 Run은 전용 409로 거부하고 변경하지 않는다")
    @ValueSource(strings = {"PENDING", "RUNNING", "COMPLETED"})
    @ParameterizedTest(name = "{0}")
    void retry_failure_originalImportRunNotRetryable(String statusValue) throws Exception {
        // given
        TestContext context = saveConnectedOwnerContext("owner");
        long originalImportRunId = saveImportRun(
                context,
                statusValue
        );
        String originalSnapshot = importRunSnapshot(originalImportRunId);
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = retry(
                originalImportRunId,
                accessTokenCookie(
                        context.memberId(),
                        "owner"
                ),
                csrfCredentials
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTION_IMPORT_NOT_RETRYABLE"));
        assertThat(importRunSnapshot(originalImportRunId)).isEqualTo(originalSnapshot);
        assertThat(importRunCount()).isOne();
    }

    @DisplayName("현재 MEMBER는 보이는 FAILED Run도 재시도할 수 없다")
    @Test
    void retry_failure_ownerRequired() throws Exception {
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
        long connectionId = saveConnection(
                workspaceId,
                ownerId
        );
        TestContext context = new TestContext(
                ownerId,
                workspaceId,
                connectionId
        );
        long originalImportRunId = saveImportRun(
                context,
                "FAILED"
        );
        String originalSnapshot = importRunSnapshot(originalImportRunId);
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = retry(
                originalImportRunId,
                accessTokenCookie(
                        memberId,
                        "member"
                ),
                csrfCredentials
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_OWNER_REQUIRED"));
        assertThat(importRunSnapshot(originalImportRunId)).isEqualTo(originalSnapshot);
        assertThat(importRunCount()).isOne();
    }

    @DisplayName("미존재 Run과 다른 Workspace Run은 같은 404 의미로 숨긴다")
    @Test
    void retry_failure_notFoundAndOtherWorkspaceHaveSameMeaning() throws Exception {
        // given
        TestContext requesterContext = saveConnectedOwnerContext("requester");
        TestContext otherContext = saveConnectedOwnerContext("other");
        long otherImportRunId = saveImportRun(
                otherContext,
                "FAILED"
        );
        String otherSnapshot = importRunSnapshot(otherImportRunId);
        CsrfCredentials csrfCredentials = csrfCredentials();
        Cookie accessTokenCookie = accessTokenCookie(
                requesterContext.memberId(),
                "requester"
        );

        // when
        MvcResult missingResult = retry(
                Long.MAX_VALUE,
                accessTokenCookie,
                csrfCredentials
        ).andReturn();
        MvcResult otherWorkspaceResult = retry(
                otherImportRunId,
                accessTokenCookie,
                csrfCredentials
        ).andReturn();

        // then
        assertThat(
                missingResult.getResponse()
                        .getStatus()
        ).isEqualTo(404);
        assertThat(
                otherWorkspaceResult.getResponse()
                        .getStatus()
        ).isEqualTo(404);
        JsonNode missingBody = objectMapper.readTree(
                missingResult.getResponse()
                        .getContentAsString()
        );
        JsonNode otherWorkspaceBody = objectMapper.readTree(
                otherWorkspaceResult.getResponse()
                        .getContentAsString()
        );
        assertThat(otherWorkspaceBody).isEqualTo(missingBody);
        assertThat(
                missingBody.get("code")
                        .asText()
        ).isEqualTo("NOTION_IMPORT_RUN_NOT_FOUND");
        assertThat(importRunSnapshot(otherImportRunId)).isEqualTo(otherSnapshot);
        assertThat(importRunCount()).isOne();
    }

    @DisplayName("Import Run ID가 양수가 아니면 400을 반환한다")
    @Test
    void retry_failure_invalidImportRunId() throws Exception {
        // given
        long memberId = saveMember("owner");
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = retry(
                0,
                accessTokenCookie(
                        memberId,
                        "owner"
                ),
                csrfCredentials
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NOTION_IMPORT_RUN_ID"));
        assertThat(importRunCount()).isZero();
    }

    @DisplayName("인증되지 않은 재시도 요청은 401을 반환하고 원본을 변경하지 않는다")
    @Test
    void retry_failure_unauthenticated() throws Exception {
        // given
        TestContext context = saveConnectedOwnerContext("owner");
        long originalImportRunId = saveImportRun(
                context,
                "FAILED"
        );
        String originalSnapshot = importRunSnapshot(originalImportRunId);
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/imports/{importRunId}/retry",
                        originalImportRunId
                ).cookie(csrfCredentials.cookie())
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(importRunSnapshot(originalImportRunId)).isEqualTo(originalSnapshot);
        assertThat(importRunCount()).isOne();
    }

    @DisplayName("CSRF 토큰이 없는 재시도 요청은 403을 반환하고 원본을 변경하지 않는다")
    @Test
    void retry_failure_missingCsrf() throws Exception {
        // given
        TestContext context = saveConnectedOwnerContext("owner");
        long originalImportRunId = saveImportRun(
                context,
                "FAILED"
        );
        String originalSnapshot = importRunSnapshot(originalImportRunId);

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/imports/{importRunId}/retry",
                        originalImportRunId
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
        assertThat(importRunSnapshot(originalImportRunId)).isEqualTo(originalSnapshot);
        assertThat(importRunCount()).isOne();
    }

    @DisplayName("Connection 승인자가 현재 OWNER가 아니면 재인증 409를 반환하고 원본을 변경하지 않는다")
    @Test
    void retry_failure_notionConnectionReauthenticationRequired() throws Exception {
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
        long connectionId = saveConnection(
                workspaceId,
                formerOwnerId
        );
        TestContext context = new TestContext(
                currentOwnerId,
                workspaceId,
                connectionId
        );
        long originalImportRunId = saveImportRun(
                context,
                "FAILED"
        );
        String originalSnapshot = importRunSnapshot(originalImportRunId);
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = retry(
                originalImportRunId,
                accessTokenCookie(
                        currentOwnerId,
                        "current-owner"
                ),
                csrfCredentials
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTION_CONNECTION_REAUTHENTICATION_REQUIRED"));
        assertThat(importRunSnapshot(originalImportRunId)).isEqualTo(originalSnapshot);
        assertThat(importRunCount()).isOne();
    }

    private ResultActions retry(
            long importRunId,
            Cookie accessTokenCookie,
            CsrfCredentials csrfCredentials
    ) throws Exception {
        return mockMvc.perform(
                post(
                        "/api/v1/imports/{importRunId}/retry",
                        importRunId
                ).cookie(
                        accessTokenCookie,
                        csrfCredentials.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );
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

    private long saveImportRun(
            TestContext context,
            String status
    ) {
        Integer totalPageCount = switch (status) {
            case "PENDING", "RUNNING" -> null;
            case "COMPLETED", "FAILED" -> 10;
            default -> throw new IllegalArgumentException("지원하지 않는 Import 상태입니다");
        };
        int processedPageCount = switch (status) {
            case "PENDING", "RUNNING" -> 0;
            case "COMPLETED" -> 10;
            case "FAILED" -> 4;
            default -> throw new IllegalArgumentException("지원하지 않는 Import 상태입니다");
        };
        String startedAt = switch (status) {
            case "PENDING" -> null;
            case "RUNNING", "COMPLETED", "FAILED" -> CREATED_AT.plusSeconds(1)
                    .toString();
            default -> throw new IllegalArgumentException("지원하지 않는 Import 상태입니다");
        };
        String completedAt = switch (status) {
            case "PENDING", "RUNNING" -> null;
            case "COMPLETED", "FAILED" -> CREATED_AT.plusSeconds(2)
                    .toString();
            default -> throw new IllegalArgumentException("지원하지 않는 Import 상태입니다");
        };
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
                    :totalPageCount,
                    :processedPageCount,
                    CAST(:startedAt AS TIMESTAMPTZ),
                    CAST(:completedAt AS TIMESTAMPTZ),
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
                        "totalPageCount",
                        totalPageCount
                )
                .param(
                        "processedPageCount",
                        processedPageCount
                )
                .param(
                        "startedAt",
                        startedAt
                )
                .param(
                        "completedAt",
                        completedAt
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

    private long activeImportRunId(long connectionId) {
        return jdbcClient.sql("""
                SELECT id
                FROM notion_import_runs
                WHERE content_source_connection_id = :connectionId
                    AND status IN ('PENDING', 'RUNNING')
                """)
                .param(
                        "connectionId",
                        connectionId
                )
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
