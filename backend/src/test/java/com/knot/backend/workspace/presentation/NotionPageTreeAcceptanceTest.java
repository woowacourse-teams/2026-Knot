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
import com.knot.backend.workspace.application.ContentSourceAuthorizationClient;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@Import({TestcontainersConfiguration.class,
        NotionPageTreeAcceptanceTest.ContentSourceAuthorizationClientTestConfiguration.class})
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotionPageTreeAcceptanceTest {
    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;
    private final ContentSourceAuthorizationClient contentSourceAuthorizationClient;

    NotionPageTreeAcceptanceTest(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            JdbcClient jdbcClient,
            ContentSourceAuthorizationClient contentSourceAuthorizationClient
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.jdbcClient = jdbcClient;
        this.contentSourceAuthorizationClient = contentSourceAuthorizationClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient.sql("""
                TRUNCATE TABLE notion_page_publications, notion_pages, notion_import_runs,
                    content_source_connections, content_source_authorizations,
                    workspace_members, workspaces, oauth_identities, members
                RESTART IDENTITY CASCADE
                """)
                .update();
        reset(contentSourceAuthorizationClient);
    }

    @DisplayName("OWNER와 MEMBER는 발행된 Page Tree를 같은 계약으로 조회하고 데이터를 변경하지 않는다")
    @ValueSource(strings = {"OWNER", "MEMBER"})
    @ParameterizedTest(name = "{0}")
    void tree_success_currentWorkspaceRoles(String role) throws Exception {
        // given
        long memberId = saveMember(role.toLowerCase());
        long workspaceId = saveWorkspace("Knot 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                role
        );
        long connectionId = saveConnection(
                workspaceId,
                memberId
        );
        long publishedRunId = saveImportRun(
                workspaceId,
                connectionId,
                memberId,
                "COMPLETED"
        );
        publish(
                workspaceId,
                publishedRunId
        );
        long rootPageId = saveNotionPage(
                workspaceId,
                publishedRunId,
                "root",
                null,
                "루트",
                "# 공개하지 않는 본문",
                0
        );
        long firstChildId = saveNotionPage(
                workspaceId,
                publishedRunId,
                "first-child",
                rootPageId,
                "첫 자식",
                "첫 본문",
                0
        );
        long secondChildId = saveNotionPage(
                workspaceId,
                publishedRunId,
                "second-child",
                rootPageId,
                "둘째 자식",
                "둘째 본문",
                1
        );
        List<String> pageSnapshot = notionPageSnapshots(workspaceId);
        String workspaceSnapshot = workspaceSnapshot(workspaceId);
        String membershipSnapshot = membershipSnapshot(
                workspaceId,
                memberId
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                memberId,
                                role.toLowerCase()
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
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(rootPageId))
                .andExpect(jsonPath("$[0].parentPageId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].title").value("루트"))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[0].notionUrl").value("https://www.notion.so/root"))
                .andExpect(jsonPath("$[0].markdownContent").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(firstChildId))
                .andExpect(jsonPath("$[1].parentPageId").value(rootPageId))
                .andExpect(jsonPath("$[2].id").value(secondChildId))
                .andExpect(jsonPath("$[2].parentPageId").value(rootPageId));
        assertThat(notionPageSnapshots(workspaceId)).isEqualTo(pageSnapshot);
        assertThat(workspaceSnapshot(workspaceId)).isEqualTo(workspaceSnapshot);
        assertThat(
                membershipSnapshot(
                        workspaceId,
                        memberId
                )
        ).isEqualTo(membershipSnapshot);
        verifyNoInteractions(contentSourceAuthorizationClient);
    }

    @DisplayName("발행된 Page가 없으면 빈 배열과 no-store를 반환한다")
    @Test
    void tree_success_emptyPublishedPages() throws Exception {
        // given
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("Knot 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                memberId,
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
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
        verifyNoInteractions(contentSourceAuthorizationClient);
    }

    @DisplayName("후속 Import가 실행 중이거나 실패해도 기존에 발행된 Page를 반환한다")
    @ValueSource(strings = {"RUNNING", "FAILED"})
    @ParameterizedTest(name = "{0}")
    void tree_success_keepsPublishedPagesDuringLaterImport(String importStatus) throws Exception {
        // given
        long memberId = saveMember("owner");
        long workspaceId = saveWorkspace("Knot 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );
        long connectionId = saveConnection(
                workspaceId,
                memberId
        );
        long publishedRunId = saveImportRun(
                workspaceId,
                connectionId,
                memberId,
                "COMPLETED"
        );
        publish(
                workspaceId,
                publishedRunId
        );
        long laterImportRunId = saveImportRun(
                workspaceId,
                connectionId,
                memberId,
                importStatus
        );
        long pageId = saveNotionPage(
                workspaceId,
                publishedRunId,
                "published",
                null,
                "기존 발행 Page",
                "기존 본문",
                0
        );
        saveNotionPage(
                workspaceId,
                laterImportRunId,
                "unpublished",
                null,
                "노출되면 안 되는 Page",
                "노출되면 안 되는 본문",
                0
        );
        List<String> pageSnapshot = notionPageSnapshots(workspaceId);
        String importRunSnapshot = importRunSnapshot(laterImportRunId);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                memberId,
                                "owner"
                        )
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(pageId))
                .andExpect(jsonPath("$[0].title").value("기존 발행 Page"));
        assertThat(notionPageSnapshots(workspaceId)).isEqualTo(pageSnapshot);
        assertThat(importRunSnapshot(laterImportRunId)).isEqualTo(importRunSnapshot);
        verifyNoInteractions(contentSourceAuthorizationClient);
    }

    @DisplayName("인증되지 않은 Page Tree 조회는 no-store와 401을 반환한다")
    @Test
    void tree_failure_unauthenticated() throws Exception {
        // given
        long workspaceId = saveWorkspace("Knot 팀");

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
                        workspaceId
                )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(contentSourceAuthorizationClient);
    }

    @DisplayName("Workspace ID가 양수가 아니면 no-store와 400을 반환한다")
    @Test
    void tree_failure_invalidWorkspaceId() throws Exception {
        // given
        long memberId = saveMember("member");

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
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
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"));
        verifyNoInteractions(contentSourceAuthorizationClient);
    }

    @DisplayName("존재하지 않는 Workspace는 no-store와 404를 반환한다")
    @Test
    void tree_failure_workspaceNotFound() throws Exception {
        // given
        long memberId = saveMember("member");

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
                        Long.MAX_VALUE
                ).cookie(
                        accessTokenCookie(
                                memberId,
                                "member"
                        )
                )
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
        verifyNoInteractions(contentSourceAuthorizationClient);
    }

    @DisplayName("Workspace 멤버가 아니면 no-store와 403을 반환한다")
    @Test
    void tree_failure_workspaceAccessDenied() throws Exception {
        // given
        long memberId = saveMember("outsider");
        long workspaceId = saveWorkspace("Knot 팀");

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                memberId,
                                "outsider"
                        )
                )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
        verifyNoInteractions(contentSourceAuthorizationClient);
    }

    @DisplayName("Page 계층에 순환 참조가 있으면 부분 응답 없이 no-store와 500을 반환한다")
    @Test
    void tree_failure_cycle() throws Exception {
        // given
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("Knot 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );
        long connectionId = saveConnection(
                workspaceId,
                memberId
        );
        long publishedRunId = saveImportRun(
                workspaceId,
                connectionId,
                memberId,
                "COMPLETED"
        );
        publish(
                workspaceId,
                publishedRunId
        );
        long firstPageId = saveNotionPage(
                workspaceId,
                publishedRunId,
                "first",
                null,
                "첫 Page",
                "첫 본문",
                0
        );
        long secondPageId = saveNotionPage(
                workspaceId,
                publishedRunId,
                "second",
                null,
                "둘째 Page",
                "둘째 본문",
                1
        );
        createCycle(
                firstPageId,
                secondPageId
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                memberId,
                                "member"
                        )
                )
        );

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.code").value("NOTION_PAGE_TREE_INVALID"))
                .andExpect(jsonPath("$.message").value("Notion Page Tree를 조회할 수 없습니다"));
        verifyNoInteractions(contentSourceAuthorizationClient);
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

    private long saveNotionPage(
            long workspaceId,
            long importRunId,
            String notionPageId,
            Long parentPageId,
            String title,
            String markdownContent,
            int position
    ) {
        return jdbcClient.sql("""
                INSERT INTO notion_pages (
                    workspace_id,
                    import_run_id,
                    notion_page_id,
                    parent_page_id,
                    title,
                    markdown_content,
                    position,
                    notion_url,
                    created_at,
                    updated_at
                ) VALUES (
                    :workspaceId,
                    :importRunId,
                    :notionPageId,
                    :parentPageId,
                    :title,
                    :markdownContent,
                    :position,
                    :notionUrl,
                    CAST(:createdAt AS TIMESTAMPTZ),
                    CAST(:createdAt AS TIMESTAMPTZ)
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "importRunId",
                        importRunId
                )
                .param(
                        "notionPageId",
                        notionPageId
                )
                .param(
                        "parentPageId",
                        parentPageId
                )
                .param(
                        "title",
                        title
                )
                .param(
                        "markdownContent",
                        markdownContent
                )
                .param(
                        "position",
                        position
                )
                .param(
                        "notionUrl",
                        "https://www.notion.so/" + notionPageId
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
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
                    :notionWorkspaceId,
                    :botId,
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
            long workspaceId,
            long connectionId,
            long memberId,
            String status
    ) {
        boolean finished = status.equals("COMPLETED") || status.equals("FAILED");
        boolean completed = status.equals("COMPLETED");
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
                    2,
                    :processedPageCount,
                    CAST(:startedAt AS TIMESTAMPTZ),
                    CAST(:completedAt AS TIMESTAMPTZ),
                    CAST(:createdAt AS TIMESTAMPTZ)
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "connectionId",
                        connectionId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "status",
                        status
                )
                .param(
                        "processedPageCount",
                        completed ? 2 : 1
                )
                .param(
                        "startedAt",
                        CREATED_AT.plusSeconds(1)
                                .toString()
                )
                .param(
                        "completedAt",
                        finished
                                ? CREATED_AT.plusSeconds(2)
                                        .toString()
                                : null
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private void publish(
            long workspaceId,
            long importRunId
    ) {
        jdbcClient.sql("""
                INSERT INTO notion_page_publications (workspace_id, published_import_run_id, published_at)
                VALUES (:workspaceId, :importRunId, CAST(:publishedAt AS TIMESTAMPTZ))
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "importRunId",
                        importRunId
                )
                .param(
                        "publishedAt",
                        CREATED_AT.plusSeconds(3)
                                .toString()
                )
                .update();
    }

    private void createCycle(
            long firstPageId,
            long secondPageId
    ) {
        jdbcClient.sql("""
                UPDATE notion_pages
                SET parent_page_id = CASE
                    WHEN id = :firstPageId THEN :secondPageId
                    WHEN id = :secondPageId THEN :firstPageId
                END
                WHERE id IN (:firstPageId, :secondPageId)
                """)
                .param(
                        "firstPageId",
                        firstPageId
                )
                .param(
                        "secondPageId",
                        secondPageId
                )
                .update();
    }

    private List<String> notionPageSnapshots(long workspaceId) {
        return jdbcClient.sql("""
                SELECT CONCAT_WS(
                    '|',
                    id,
                    import_run_id,
                    notion_page_id,
                    COALESCE(parent_page_id::text, 'null'),
                    title,
                    markdown_content,
                    position,
                    notion_url,
                    created_at::text,
                    updated_at::text
                )
                FROM notion_pages
                WHERE workspace_id = :workspaceId
                ORDER BY id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(String.class)
                .list();
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

    private String importRunSnapshot(long importRunId) {
        return jdbcClient.sql("""
                SELECT CONCAT_WS(
                    '|',
                    workspace_id,
                    content_source_connection_id,
                    requested_by_member_id,
                    status,
                    total_page_count,
                    processed_page_count,
                    started_at::text,
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

    @TestConfiguration(proxyBeanMethods = false)
    static class ContentSourceAuthorizationClientTestConfiguration {

        @Bean("notionPageTreeNoCallClient")
        @Primary
        ContentSourceAuthorizationClient notionPageTreeNoCallClient() {
            return mock(ContentSourceAuthorizationClient.class);
        }
    }
}
