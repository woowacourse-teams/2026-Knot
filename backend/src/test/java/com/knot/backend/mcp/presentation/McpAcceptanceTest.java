package com.knot.backend.mcp.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.mcp.application.McpProtocolVersions;
import com.knot.backend.mcp.application.McpRequestHandler;
import com.knot.backend.mcp.application.McpToolCatalog;
import com.knot.backend.mcp.domain.McpErrorCode;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hamcrest.Matchers;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 원격 MCP 서버(기획서 6.6, 로드맵 A13)를 켠 상태의 HTTP 종단. 인증은 기존 Bearer 액세스 토큰 하나이며 전용 연결
 * 토큰이 없다. 브라우저에서 오는 요청(`Origin` 있음)은 막고, 무상태 서버라 GET·DELETE는 405다.
 */
@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@TestPropertySource(properties = "mcp.remote.enabled=true")
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class McpAcceptanceTest {
    private static final String MCP_PATH = "/mcp";
    private static final OffsetDateTime CREATED_AT = Instant.parse("2026-08-30T00:00:00Z")
            .atOffset(ZoneOffset.UTC);

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;

    McpAcceptanceTest(
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
                TRUNCATE TABLE search_references, search_document_chunks, chat_feedback, chat_messages, chat_sessions,
                    imported_page_publications, imported_pages, content_import_runs,
                    content_source_connections, content_source_authorizations,
                    workspace_members, workspaces, members RESTART IDENTITY CASCADE
                """)
                .update();
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이라 MCP 요청 자체가 서지 않는다")
    void post_failure_withoutAccessToken() throws Exception {
        // given
        String body = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                """;

        // when
        ResultActions result = mockMvc.perform(
                post(MCP_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        // then
        result.andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bearer 액세스 토큰으로 initialize하면 서버 정보와 instructions를 돌려준다")
    void post_success_initialize() throws Exception {
        // given
        long memberId = saveMember("mcp-member");

        // when
        ResultActions result = perform(
                memberId,
                """
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}
                        """
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
                .andExpect(jsonPath("$.result.serverInfo.name").value(McpRequestHandler.SERVER_NAME))
                .andExpect(jsonPath("$.result.capabilities.tools.listChanged").value(false))
                .andExpect(jsonPath("$.result.instructions").value(Matchers.containsString("search_documents")));
    }

    @Test
    @DisplayName("tools/list는 도구 두 개와 입력 스키마를 돌려준다")
    void post_success_toolsList() throws Exception {
        // given
        long memberId = saveMember("mcp-member");

        // when
        ResultActions result = perform(
                memberId,
                """
                        {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                        """
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.result.tools",
                                Matchers.hasSize(2)
                        )
                )
                .andExpect(jsonPath("$.result.tools[0].name").value(McpToolCatalog.LIST_WORKSPACES))
                .andExpect(jsonPath("$.result.tools[1].name").value(McpToolCatalog.SEARCH_DOCUMENTS))
                .andExpect(jsonPath("$.result.tools[1].inputSchema.required[0]").value("query"));
    }

    @Test
    @DisplayName("list_workspaces는 그 회원이 속한 워크스페이스만 돌려준다")
    void post_success_listWorkspaces() throws Exception {
        // given
        long memberId = saveMember("mcp-member");
        long outsiderId = saveMember("mcp-outsider");
        long workspaceId = saveWorkspace("탐색 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId
        );
        long otherWorkspaceId = saveWorkspace("남의 팀");
        saveWorkspaceMember(
                otherWorkspaceId,
                outsiderId
        );

        // when
        ResultActions result = perform(
                memberId,
                """
                        {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_workspaces"}}
                        """
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.result.structuredContent.workspaces",
                                Matchers.hasSize(1)
                        )
                )
                .andExpect(jsonPath("$.result.structuredContent.workspaces[0].id").value(workspaceId))
                .andExpect(jsonPath("$.result.content[0].text").value(Matchers.containsString("탐색 팀")));
    }

    @Test
    @DisplayName("문서가 준비되지 않은 워크스페이스면 서버 오류 코드를 isError로 중계한다")
    void post_success_searchDocumentsRelaysNotReady() throws Exception {
        // given
        long memberId = saveMember("mcp-member");
        long workspaceId = saveWorkspace("탐색 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId
        );

        // when
        ResultActions result = perform(
                memberId,
                """
                        {"jsonrpc":"2.0","id":4,"method":"tools/call",
                         "params":{"name":"search_documents","arguments":{"query":"왜 PostgreSQL인가요?"}}}
                        """
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(
                        jsonPath("$.result.content[0].text")
                                .value(Matchers.startsWith(ChatErrorCode.CHAT_DOCUMENTS_NOT_READY.getCode() + ": "))
                );
    }

    @Test
    @DisplayName("속한 워크스페이스가 없으면 안내 문구를 isError로 돌려준다")
    void post_success_searchDocumentsWithoutWorkspace() throws Exception {
        // given
        long memberId = saveMember("mcp-member");

        // when
        ResultActions result = perform(
                memberId,
                """
                        {"jsonrpc":"2.0","id":5,"method":"tools/call",
                         "params":{"name":"search_documents","arguments":{"query":"질문"}}}
                        """
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(
                        jsonPath("$.result.content[0].text")
                                .value(Matchers.startsWith("NO_WORKSPACE: "))
                );
    }

    @Test
    @DisplayName("남의 워크스페이스를 지정하면 서버의 멤버 검사 코드를 그대로 중계한다")
    void post_success_searchDocumentsRelaysForbidden() throws Exception {
        // given
        long memberId = saveMember("mcp-member");
        long outsiderId = saveMember("mcp-outsider");
        long otherWorkspaceId = saveWorkspace("남의 팀");
        saveWorkspaceMember(
                otherWorkspaceId,
                outsiderId
        );

        // when
        ResultActions result = perform(
                memberId,
                """
                        {"jsonrpc":"2.0","id":6,"method":"tools/call",
                         "params":{"name":"search_documents","arguments":{"query":"질문","workspaceId":%d}}}
                        """.formatted(otherWorkspaceId)
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    @DisplayName("브라우저에서 온 요청(Origin 있음)은 인증돼 있어도 거부한다")
    void post_failure_originPresent() throws Exception {
        // given
        long memberId = saveMember("mcp-member");

        // when
        ResultActions result = mockMvc.perform(
                post(MCP_PATH).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(memberId)
                )
                        .header(
                                HttpHeaders.ORIGIN,
                                "https://knoted.kr"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":7,"method":"ping"}
                                """)
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(McpErrorCode.MCP_ORIGIN_NOT_ALLOWED.getCode()));
    }

    @Test
    @DisplayName("지원하지 않는 MCP-Protocol-Version 헤더는 400으로 거부한다")
    void post_failure_unsupportedProtocolVersion() throws Exception {
        // given
        long memberId = saveMember("mcp-member");

        // when
        ResultActions result = mockMvc.perform(
                post(MCP_PATH).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(memberId)
                )
                        .header(
                                "MCP-Protocol-Version",
                                "1999-01-01"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":8,"method":"ping"}
                                """)
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(McpErrorCode.MCP_PROTOCOL_VERSION_UNSUPPORTED.getCode()));
    }

    @Test
    @DisplayName("지원 목록 안의 MCP-Protocol-Version 헤더는 통과한다")
    void post_success_supportedProtocolVersionHeader() throws Exception {
        // given
        long memberId = saveMember("mcp-member");

        // when
        ResultActions result = mockMvc.perform(
                post(MCP_PATH).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(memberId)
                )
                        .header(
                                "MCP-Protocol-Version",
                                McpProtocolVersions.LATEST
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":9,"method":"ping"}
                                """)
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.result").exists());
    }

    @Test
    @DisplayName("무상태 서버라 GET·DELETE는 405로 POST만 알린다")
    void getAndDelete_failure_methodNotAllowed() throws Exception {
        // given
        long memberId = saveMember("mcp-member");

        // when
        ResultActions getResult = mockMvc.perform(
                get(MCP_PATH).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(memberId)
                )
        );
        ResultActions deleteResult = mockMvc.perform(
                delete(MCP_PATH).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(memberId)
                )
        );

        // then
        getResult.andExpect(status().isMethodNotAllowed())
                .andExpect(
                        header().string(
                                HttpHeaders.ALLOW,
                                "POST"
                        )
                )
                .andExpect(jsonPath("$.code").value(McpController.METHOD_NOT_ALLOWED_CODE));
        deleteResult.andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("알림은 본문 없이 202로 받는다")
    void post_success_notificationAccepted() throws Exception {
        // given
        long memberId = saveMember("mcp-member");

        // when
        ResultActions result = perform(
                memberId,
                """
                        {"jsonrpc":"2.0","method":"notifications/initialized"}
                        """
        );

        // then
        result.andExpect(status().isAccepted())
                .andExpect(content().string(""));
    }

    private ResultActions perform(
            long memberId,
            String body
    ) throws Exception {
        return mockMvc.perform(
                post(MCP_PATH).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(memberId)
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );
    }

    /** 인증 자격증명은 `Authorization: Bearer` 하나뿐이다(기획서 5.1). MCP 전용 연결 토큰은 없다(6.6) */
    private String bearerToken(long memberId) {
        return "Bearer " + authTokenProvider.issue(
                AuthenticatedMember.of(
                        memberId,
                        "mcp-member",
                        null
                )
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
                VALUES (:name, :createdAt)
                RETURNING id
                """)
                .param(
                        "name",
                        name
                )
                .param(
                        "createdAt",
                        CREATED_AT
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
                VALUES (:workspaceId, :memberId, 'MEMBER', :joinedAt)
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
                        CREATED_AT
                )
                .update();
    }
}
