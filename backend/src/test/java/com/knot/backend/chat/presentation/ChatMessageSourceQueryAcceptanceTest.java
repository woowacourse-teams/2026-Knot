package com.knot.backend.chat.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChatMessageSourceQueryAcceptanceTest {
    private static final String ACCESS_TOKEN_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T00:00:00Z");
    private static final OffsetDateTime CREATED_AT_OFFSET = CREATED_AT.atOffset(ZoneOffset.UTC);

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;

    ChatMessageSourceQueryAcceptanceTest(
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
        jdbcClient.sql("""
                TRUNCATE TABLE search_references, chat_feedback, chat_messages, chat_sessions,
                    imported_page_publications, imported_pages, content_import_runs,
                    content_source_connections, content_source_authorizations,
                    workspace_members, workspaces, members RESTART IDENTITY CASCADE
                """)
                .update();
    }

    @Test
    @DisplayName("assistant 메시지의 저장된 Notion 출처를 rank 순서로 반환한다")
    void findSources_success() throws Exception {
        // given
        Fixture fixture = saveFixture();
        saveSearchReference(fixture);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/messages/{messageId}/sources",
                        fixture.messageId()
                ).cookie(accessTokenCookie(fixture.ownerId()))
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.searchReferences").isArray())
                .andExpect(jsonPath("$.searchReferences").isNotEmpty())
                .andExpect(jsonPath("$.searchReferences[0].id").isNumber())
                .andExpect(jsonPath("$.searchReferences[0].messageId").value(fixture.messageId()))
                .andExpect(jsonPath("$.searchReferences[0].rank").value(1))
                .andExpect(jsonPath("$.searchReferences[0].relevanceScore").value(0.9472))
                .andExpect(jsonPath("$.searchReferences[0].source").value("NOTION"))
                .andExpect(jsonPath("$.searchReferences[0].notionPage.id").value("notion-page-1"))
                .andExpect(jsonPath("$.searchReferences[0].notionPage.title").value("기술 스택과 라이브러리 도입"))
                .andExpect(
                        jsonPath("$.searchReferences[0].notionPage.notionUrl")
                                .value("https://www.notion.so/notion-page-1")
                )
                .andExpect(jsonPath("$.searchReferences[0].notionPage.createdAt").value("2026-08-30T00:00:00Z"))
                .andExpect(jsonPath("$.searchReferences[0].notionPage.updatedAt").value("2026-08-30T01:00:00Z"))
                .andExpect(jsonPath("$.searchReferences[1].rank").value(2))
                .andExpect(jsonPath("$.searchReferences[1].relevanceScore").value(0.8))
                .andExpect(jsonPath("$.searchReferences[1].notionPage.id").value("notion-page-2"));
    }

    @Test
    @DisplayName("출처가 없는 메시지는 빈 searchReferences를 반환한다")
    void findSources_success_empty() throws Exception {
        // given
        Fixture fixture = saveFixture();

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/messages/{messageId}/sources",
                        fixture.messageId()
                ).cookie(accessTokenCookie(fixture.ownerId()))
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.searchReferences").isArray())
                .andExpect(jsonPath("$.searchReferences").isEmpty());
    }

    @Test
    @DisplayName("다른 멤버는 메시지 출처를 조회할 수 없다")
    void findSources_failure_accessDenied() throws Exception {
        // given
        Fixture fixture = saveFixture();
        saveSearchReference(fixture);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/messages/{messageId}/sources",
                        fixture.messageId()
                ).cookie(accessTokenCookie(fixture.otherMemberId()))
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("존재하지 않는 메시지의 출처를 조회하면 404를 반환한다")
    void findSources_failure_messageNotFound() throws Exception {
        // given
        Fixture fixture = saveFixture();

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/messages/{messageId}/sources",
                        fixture.messageId() + 1000
                ).cookie(accessTokenCookie(fixture.ownerId()))
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("메시지 ID가 양수가 아니면 400을 반환한다")
    void findSources_failure_invalidMessageId() throws Exception {
        // given
        Fixture fixture = saveFixture();

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/messages/{messageId}/sources",
                        -1L
                ).cookie(accessTokenCookie(fixture.ownerId()))
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CHAT_MESSAGE_ID"));
    }

    @Test
    @DisplayName("메시지와 다른 workspace에 저장된 출처는 반환하지 않는다")
    void findSources_success_excludesDifferentWorkspaceReference() throws Exception {
        // given
        Fixture fixture = saveFixture();
        saveCrossWorkspaceReference(fixture);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/messages/{messageId}/sources",
                        fixture.messageId()
                ).cookie(accessTokenCookie(fixture.ownerId()))
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.searchReferences").isArray())
                .andExpect(jsonPath("$.searchReferences").isEmpty());
    }

    @Test
    @DisplayName("사용자 메시지는 출처 조회 대상이 아니다")
    void findSources_failure_userMessage() throws Exception {
        // given
        Fixture fixture = saveFixture();
        long userMessageId = jdbcClient.sql("""
                INSERT INTO chat_messages (session_id, role, content, created_at)
                VALUES (:sessionId, 'USER', 'PostgreSQL을 왜 사용했나요?', :createdAt)
                RETURNING id
                """)
                .param(
                        "sessionId",
                        fixture.sessionId()
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET.plusSeconds(2)
                )
                .query(Long.class)
                .single();

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/messages/{messageId}/sources",
                        userMessageId
                ).cookie(accessTokenCookie(fixture.ownerId()))
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));
    }

    private Fixture saveFixture() {
        long ownerId = saveMember("source-owner");
        long otherMemberId = saveMember("source-member");
        long workspaceId = jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES ('출처 조회 팀', :createdAt)
                RETURNING id
                """)
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .query(Long.class)
                .single();
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                otherMemberId,
                "MEMBER"
        );
        long sessionId = jdbcClient.sql("""
                INSERT INTO chat_sessions (workspace_id, member_id, title, created_at, last_message_at)
                VALUES (:workspaceId, :memberId, '출처 조회', :createdAt, :createdAt)
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        ownerId
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .query(Long.class)
                .single();
        long messageId = jdbcClient.sql("""
                INSERT INTO chat_messages (session_id, role, content, created_at)
                VALUES (:sessionId, 'ASSISTANT', 'PostgreSQL을 사용합니다.', :createdAt)
                RETURNING id
                """)
                .param(
                        "sessionId",
                        sessionId
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET.plusSeconds(1)
                )
                .query(Long.class)
                .single();
        long connectionId = jdbcClient.sql("""
                INSERT INTO content_source_connections (
                    workspace_id, provider, access_credential_ciphertext,
                    external_source_id, provider_connection_id, authorization_owner_type,
                    authorizing_member_id, created_at, updated_at
                ) VALUES (
                    :workspaceId, 'NOTION', 'test-ciphertext',
                    'notion-workspace', 'notion-connection', 'WORKSPACE',
                    :memberId, :createdAt, :createdAt
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        ownerId
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .query(Long.class)
                .single();
        long importRunId = jdbcClient.sql("""
                INSERT INTO content_import_runs (
                    workspace_id, content_source_connection_id, requested_by_member_id,
                    status, total_page_count, processed_page_count,
                    started_at, completed_at, created_at
                ) VALUES (
                    :workspaceId, :connectionId, :memberId,
                    'COMPLETED', 1, 1,
                    :createdAt, :updatedAt, :createdAt
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
                        ownerId
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .param(
                        "updatedAt",
                        CREATED_AT_OFFSET.plusSeconds(1)
                )
                .query(Long.class)
                .single();
        long importedPageId = jdbcClient.sql("""
                INSERT INTO imported_pages (
                    workspace_id, import_run_id, external_page_id, title,
                    markdown_content, position, source_url, created_at, updated_at
                ) VALUES (
                    :workspaceId, :importRunId, 'notion-page-1', '기술 스택과 라이브러리 도입',
                    'PostgreSQL을 사용합니다.', 0, 'https://www.notion.so/notion-page-1',
                    :createdAt, :updatedAt
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
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .param(
                        "updatedAt",
                        CREATED_AT_OFFSET.plusSeconds(3600)
                )
                .query(Long.class)
                .single();
        long secondImportedPageId = jdbcClient.sql("""
                INSERT INTO imported_pages (
                    workspace_id, import_run_id, external_page_id, title,
                    markdown_content, position, source_url, created_at, updated_at
                ) VALUES (
                    :workspaceId, :importRunId, 'notion-page-2', '백엔드 회의록',
                    'PostgreSQL을 사용하기로 결정했습니다.', 1, 'https://www.notion.so/notion-page-2',
                    :createdAt, :updatedAt
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
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .param(
                        "updatedAt",
                        CREATED_AT_OFFSET.plusSeconds(3600)
                )
                .query(Long.class)
                .single();
        return new Fixture(
                ownerId,
                otherMemberId,
                messageId,
                sessionId,
                workspaceId,
                importRunId,
                importedPageId,
                secondImportedPageId
        );
    }

    private void saveSearchReference(Fixture fixture) {
        jdbcClient.sql("""
                INSERT INTO search_references (
                    message_id, workspace_id, import_run_id, imported_page_id,
                    reference_rank, relevance_score
                ) VALUES (
                    :messageId, :workspaceId, :importRunId, :importedPageId, 1, 0.9472
                ), (
                    :messageId, :workspaceId, :importRunId, :secondImportedPageId, 2, 0.8
                )
                """)
                .param(
                        "messageId",
                        fixture.messageId()
                )
                .param(
                        "workspaceId",
                        fixture.workspaceId()
                )
                .param(
                        "importRunId",
                        fixture.importRunId()
                )
                .param(
                        "importedPageId",
                        fixture.importedPageId()
                )
                .param(
                        "secondImportedPageId",
                        fixture.secondImportedPageId()
                )
                .update();
    }

    private void saveCrossWorkspaceReference(Fixture fixture) {
        jdbcClient.sql("""
                WITH foreign_workspace AS (
                    INSERT INTO workspaces (name, created_at)
                    VALUES ('다른 팀', :createdAt)
                    RETURNING id
                ), foreign_membership AS (
                    INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                    SELECT id, :memberId, 'OWNER', :createdAt
                    FROM foreign_workspace
                    RETURNING workspace_id, member_id
                ), foreign_connection AS (
                    INSERT INTO content_source_connections (
                        workspace_id, provider, access_credential_ciphertext,
                        external_source_id, provider_connection_id, authorization_owner_type,
                        authorizing_member_id, created_at, updated_at
                    )
                    SELECT workspace_id, 'NOTION', 'foreign-ciphertext',
                        'foreign-notion-workspace', 'foreign-notion-connection', 'WORKSPACE',
                        member_id, :createdAt, :createdAt
                    FROM foreign_membership
                    RETURNING id, workspace_id
                ), foreign_import_run AS (
                    INSERT INTO content_import_runs (
                        workspace_id, content_source_connection_id, requested_by_member_id,
                        status, total_page_count, processed_page_count,
                        started_at, completed_at, created_at
                    )
                    SELECT workspace_id, id, :memberId,
                        'COMPLETED', 1, 1,
                        :createdAt, :updatedAt, :createdAt
                    FROM foreign_connection
                    RETURNING id, workspace_id
                ), foreign_page AS (
                    INSERT INTO imported_pages (
                        workspace_id, import_run_id, external_page_id, title,
                        markdown_content, position, source_url, created_at, updated_at
                    )
                    SELECT workspace_id, id, 'foreign-page', '다른 팀 문서',
                        '다른 팀의 문서 내용', 0, 'https://www.notion.so/foreign-page',
                        :createdAt, :updatedAt
                    FROM foreign_import_run
                    RETURNING id, workspace_id, import_run_id
                )
                INSERT INTO search_references (
                    message_id, workspace_id, import_run_id, imported_page_id,
                    reference_rank, relevance_score
                )
                SELECT :messageId, workspace_id, import_run_id, id, 1, 0.99
                FROM foreign_page
                """)
                .param(
                        "memberId",
                        fixture.ownerId()
                )
                .param(
                        "messageId",
                        fixture.messageId()
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .param(
                        "updatedAt",
                        CREATED_AT_OFFSET.plusSeconds(1)
                )
                .update();
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

    private void saveWorkspaceMember(
            long workspaceId,
            long memberId,
            String role
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, :role, :joinedAt)
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
                        CREATED_AT_OFFSET
                )
                .update();
    }

    private Cookie accessTokenCookie(long memberId) {
        return new Cookie(
                ACCESS_TOKEN_COOKIE_NAME,
                authTokenProvider.issue(
                        AuthenticatedMember.of(
                                memberId,
                                "source-member",
                                null
                        )
                )
        );
    }

    private record Fixture(
            long ownerId,
            long otherMemberId,
            long messageId,
            long sessionId,
            long workspaceId,
            long importRunId,
            long importedPageId,
            long secondImportedPageId
    ) {
    }
}
