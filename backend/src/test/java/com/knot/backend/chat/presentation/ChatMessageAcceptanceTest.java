package com.knot.backend.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
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
@TestConstructor(autowireMode = AutowireMode.ALL)
class ChatMessageAcceptanceTest {
    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final ObjectMapper objectMapper;
    private final JdbcClient jdbcClient;

    ChatMessageAcceptanceTest(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            ObjectMapper objectMapper,
            JdbcClient jdbcClient
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.objectMapper = objectMapper;
        this.jdbcClient = jdbcClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient
                .sql(
                        "TRUNCATE TABLE chat_messages, chat_sessions, workspace_members, workspaces, members "
                                + "RESTART IDENTITY CASCADE"
                )
                .update();
    }

    @Test
    @DisplayName("공개된 문서가 없으면 채팅 요청을 409로 거절하고 USER 메시지를 저장하지 않는다")
    void sendMessage_failure_documentsNotReady() throws Exception {
        // given
        long memberId = saveMember();
        long workspaceId = saveWorkspace();
        saveWorkspaceMember(
                workspaceId,
                memberId
        );
        long sessionId = saveChatSession(
                workspaceId,
                memberId
        );
        OffsetDateTime lastMessageAt = singleLastMessageAt(sessionId);
        Cookie accessTokenCookie = accessTokenCookie(memberId);
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/conversations/{sessionId}/messages",
                        sessionId
                ).cookie(
                        accessTokenCookie,
                        csrfCredentials.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"질문"}
                                """)
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_DOCUMENTS_NOT_READY"))
                .andExpect(jsonPath("$.message").value("문서 동기화가 완료된 후 검색할 수 있습니다"));
        assertThat(countChatMessages(sessionId)).isZero();
        assertThat(singleLastMessageAt(sessionId)).isEqualTo(lastMessageAt);
    }

    private Cookie accessTokenCookie(long memberId) {
        String token = authTokenProvider.issue(
                AuthenticatedMember.of(
                        memberId,
                        "octocat",
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

    private long saveMember() {
        return jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES ('octocat', NULL)
                RETURNING id
                """)
                .query(Long.class)
                .single();
    }

    private long saveWorkspace() {
        return jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES ('Knot 팀', TIMESTAMPTZ '2026-09-02 00:00:00Z')
                RETURNING id
                """)
                .query(Long.class)
                .single();
    }

    private void saveWorkspaceMember(
            long workspaceId,
            long memberId
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at, last_viewed)
                VALUES (:workspaceId, :memberId, 'OWNER', TIMESTAMPTZ '2026-09-02 00:00:00Z', FALSE)
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .update();
    }

    private long saveChatSession(
            long workspaceId,
            long memberId
    ) {
        return jdbcClient.sql("""
                INSERT INTO chat_sessions (workspace_id, member_id, title, created_at, last_message_at)
                VALUES (
                    :workspaceId,
                    :memberId,
                    '새 대화',
                    TIMESTAMPTZ '2026-09-02 00:00:00Z',
                    TIMESTAMPTZ '2026-09-02 00:00:00Z'
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .query(Long.class)
                .single();
    }

    private long countChatMessages(long sessionId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM chat_messages WHERE session_id = :sessionId")
                .param(
                        "sessionId",
                        sessionId
                )
                .query(Long.class)
                .single();
    }

    private OffsetDateTime singleLastMessageAt(long sessionId) {
        return jdbcClient.sql("SELECT last_message_at FROM chat_sessions WHERE id = :sessionId")
                .param(
                        "sessionId",
                        sessionId
                )
                .query(OffsetDateTime.class)
                .single();
    }

    private record CsrfCredentials(
            Cookie cookie,
            String token
    ) {
    }
}
