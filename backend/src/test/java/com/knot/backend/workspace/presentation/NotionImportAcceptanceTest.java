package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.NotionOAuthClient;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@Import({TestcontainersConfiguration.class, NotionImportAcceptanceTest.NotionOAuthClientTestConfiguration.class})
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotionImportAcceptanceTest {
    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;
    private final NotionOAuthClient notionOAuthClient;

    NotionImportAcceptanceTest(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            JdbcClient jdbcClient,
            NotionOAuthClient notionOAuthClient
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.jdbcClient = jdbcClient;
        this.notionOAuthClient = notionOAuthClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient.sql("""
                TRUNCATE TABLE notion_import_runs, notion_connections, notion_oauth_authorizations,
                    workspace_members, workspaces, oauth_identities, members
                RESTART IDENTITY CASCADE
                """)
                .update();
        reset(notionOAuthClient);
    }

    @DisplayName("MEMBER는 네 Import 상태를 조회해도 데이터를 변경하지 않는다")
    @MethodSource("statusCases")
    @ParameterizedTest(name = "{0}")
    void status_success_statusContract(
            String statusValue,
            Integer totalPageCount,
            int processedPageCount,
            Instant startedAt,
            Instant completedAt,
            String publicFailureReason
    ) throws Exception {
        // given
        TestContext context = saveContext(
                "member",
                "MEMBER"
        );
        long importRunId = saveImportRun(
                context,
                statusValue,
                totalPageCount,
                processedPageCount,
                startedAt,
                completedAt
        );
        String importRunSnapshot = importRunSnapshot(importRunId);
        String connectionSnapshot = connectionSnapshot(context.connectionId());
        String membershipSnapshot = membershipSnapshot(
                context.workspaceId(),
                context.memberId()
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/imports/{importRunId}",
                        importRunId
                ).cookie(
                        accessTokenCookie(
                                context.memberId(),
                                "member"
                        )
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.id").value(importRunId))
                .andExpect(jsonPath("$.status").value(statusValue))
                .andExpect(jsonPath("$.processedPageCount").value(processedPageCount))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()));
        expectNullableValue(
                result,
                "$.totalPageCount",
                totalPageCount
        );
        expectNullableValue(
                result,
                "$.failureReason",
                publicFailureReason
        );
        expectNullableValue(
                result,
                "$.startedAt",
                instantValue(startedAt)
        );
        expectNullableValue(
                result,
                "$.completedAt",
                instantValue(completedAt)
        );
        assertThat(importRunSnapshot(importRunId)).isEqualTo(importRunSnapshot);
        assertThat(connectionSnapshot(context.connectionId())).isEqualTo(connectionSnapshot);
        assertThat(
                membershipSnapshot(
                        context.workspaceId(),
                        context.memberId()
                )
        ).isEqualTo(membershipSnapshot);
        verifyNoInteractions(notionOAuthClient);
    }

    @DisplayName("현재 Workspace의 OWNER와 MEMBER는 역할 차이 없이 Import 상태를 조회한다")
    @ValueSource(strings = {"OWNER", "MEMBER"})
    @ParameterizedTest(name = "{0}")
    void status_success_currentWorkspaceRoles(String role) throws Exception {
        // given
        TestContext context = saveContext(
                role.toLowerCase(),
                role
        );
        long importRunId = saveImportRun(
                context,
                "PENDING",
                null,
                0,
                null,
                null
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/imports/{importRunId}",
                        importRunId
                ).cookie(
                        accessTokenCookie(
                                context.memberId(),
                                role.toLowerCase()
                        )
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @DisplayName("인증되지 않은 Import 상태 조회 요청은 401을 반환한다")
    @Test
    void status_failure_unauthenticated() throws Exception {
        // given
        long importRunId = 1L;

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/imports/{importRunId}",
                        importRunId
                )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다"));
        verifyNoInteractions(notionOAuthClient);
    }

    @DisplayName("Import Run ID가 양수가 아니면 400을 반환한다")
    @Test
    void status_failure_invalidImportRunId() throws Exception {
        // given
        long memberId = saveMember("member");

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/imports/{importRunId}",
                        0
                ).cookie(
                        accessTokenCookie(
                                memberId,
                                "member"
                        )
                )
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NOTION_IMPORT_RUN_ID"))
                .andExpect(jsonPath("$.message").value("Notion Import 실행 ID가 올바르지 않습니다"));
        verifyNoInteractions(notionOAuthClient);
    }

    @DisplayName("미존재 Import와 다른 Workspace의 Import는 같은 404를 반환한다")
    @Test
    void status_failure_hidesImportRunExistence() throws Exception {
        // given
        TestContext ownerContext = saveContext(
                "owner",
                "OWNER"
        );
        long importRunId = saveImportRun(
                ownerContext,
                "RUNNING",
                10,
                4,
                CREATED_AT.plusSeconds(1),
                null
        );
        TestContext outsiderContext = saveContext(
                "outsider",
                "OWNER"
        );
        Cookie outsiderCookie = accessTokenCookie(
                outsiderContext.memberId(),
                "outsider"
        );

        // when
        MvcResult otherWorkspaceResult = mockMvc.perform(
                get(
                        "/api/v1/imports/{importRunId}",
                        importRunId
                ).cookie(outsiderCookie)
        )
                .andExpect(status().isNotFound())
                .andReturn();
        MvcResult missingResult = mockMvc.perform(
                get(
                        "/api/v1/imports/{importRunId}",
                        Long.MAX_VALUE
                ).cookie(outsiderCookie)
        )
                .andExpect(status().isNotFound())
                .andReturn();

        // then
        assertThat(
                otherWorkspaceResult.getResponse()
                        .getContentAsString()
        ).isEqualTo(
                missingResult.getResponse()
                        .getContentAsString()
        );
        assertThat(
                missingResult.getResponse()
                        .getContentAsString()
        ).contains(
                "NOTION_IMPORT_RUN_NOT_FOUND",
                "Notion Import 실행을 찾을 수 없습니다"
        );
        verifyNoInteractions(notionOAuthClient);
    }

    private void expectNullableValue(
            ResultActions result,
            String path,
            Object value
    ) throws Exception {
        if (value == null) {
            result.andExpect(jsonPath(path).value(Matchers.nullValue()));
            return;
        }
        result.andExpect(jsonPath(path).value(value));
    }

    private String instantValue(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private TestContext saveContext(
            String nickname,
            String role
    ) {
        long memberId = saveMember(nickname);
        long workspaceId = saveWorkspace(nickname + " 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                role
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
                INSERT INTO notion_connections (
                    workspace_id,
                    access_token_ciphertext,
                    notion_workspace_id,
                    bot_id,
                    owner_type,
                    authorizing_member_id,
                    created_at,
                    updated_at,
                    version
                ) VALUES (
                    :workspaceId,
                    'encrypted-access-token',
                    :notionWorkspaceId,
                    :botId,
                    'workspace',
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
                        "notionWorkspaceId",
                        "notion-workspace-" + workspaceId
                )
                .param(
                        "botId",
                        "bot-" + workspaceId
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
            String status,
            Integer totalPageCount,
            int processedPageCount,
            Instant startedAt,
            Instant completedAt
    ) {
        return jdbcClient.sql("""
                INSERT INTO notion_import_runs (
                    workspace_id,
                    notion_connection_id,
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
                        instantValue(startedAt)
                )
                .param(
                        "completedAt",
                        instantValue(completedAt)
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
                    notion_connection_id,
                    requested_by_member_id,
                    status,
                    COALESCE(total_page_count::text, 'null'),
                    processed_page_count,
                    COALESCE(started_at::text, 'null'),
                    COALESCE(completed_at::text, 'null'),
                    created_at::text
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

    private String connectionSnapshot(long connectionId) {
        return jdbcClient.sql("""
                SELECT CONCAT_WS(
                    '|',
                    workspace_id,
                    notion_workspace_id,
                    bot_id,
                    authorizing_member_id,
                    updated_at::text,
                    version
                )
                FROM notion_connections
                WHERE id = :connectionId
                """)
                .param(
                        "connectionId",
                        connectionId
                )
                .query(String.class)
                .single();
    }

    private String membershipSnapshot(
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

    private static Stream<Arguments> statusCases() {
        return Stream.of(
                Arguments.of(
                        "PENDING",
                        null,
                        0,
                        null,
                        null,
                        null
                ),
                Arguments.of(
                        "RUNNING",
                        10,
                        4,
                        CREATED_AT.plusSeconds(1),
                        null,
                        null
                ),
                Arguments.of(
                        "COMPLETED",
                        10,
                        10,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2),
                        null
                ),
                Arguments.of(
                        "FAILED",
                        10,
                        4,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2),
                        "Notion 문서를 가져오지 못했습니다"
                )
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class NotionOAuthClientTestConfiguration {

        @Bean("notionImportNoCallClient")
        @Primary
        NotionOAuthClient notionImportNoCallClient() {
            return mock(NotionOAuthClient.class);
        }
    }

    private record TestContext(
            long memberId,
            long workspaceId,
            long connectionId
    ) {
    }
}
