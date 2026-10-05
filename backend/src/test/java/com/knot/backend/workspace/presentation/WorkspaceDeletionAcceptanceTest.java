package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
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
class WorkspaceDeletionAcceptanceTest {
    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CONTROL_TOKEN = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefA";
    private static final Instant CREATED_AT = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant JOINED_AT = Instant.parse("2026-10-05T00:01:00Z");

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final ObjectMapper objectMapper;
    private final JdbcClient jdbcClient;

    WorkspaceDeletionAcceptanceTest(
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
        jdbcClient.sql("""
                TRUNCATE TABLE recording_sessions, workspace_invitations, workspace_members, workspaces,
                    oauth_identities, members
                RESTART IDENTITY CASCADE
                """)
                .update();
    }

    @Test
    @DisplayName("OWNER 삭제는 204를 반환하고 멤버의 목록·상세 접근과 진행 중 녹음을 함께 정리한다")
    void delete_success_removesAccessAndDiscardsRecording() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("삭제 팀");
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
        markLastViewed(
                workspaceId,
                memberId
        );
        long recordingId = startRecording(
                workspaceId,
                memberId
        );

        // when
        ResultActions result = deleteWorkspace(
                Long.toString(workspaceId),
                ownerId
        );

        // then
        result.andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(recordingStatus(recordingId)).isEqualTo("DISCARDED");
        Cookie memberCookie = accessTokenCookie(memberId);
        mockMvc.perform(get("/api/v1/workspaces").cookie(memberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaces").isEmpty())
                .andExpect(jsonPath("$.lastViewedWorkspaceId").isEmpty());
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId).cookie(memberCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제된 워크스페이스의 기존 초대 미리보기와 참여, 새 녹음 시작은 404로 거절한다")
    void delete_success_blocksInvitationAndRecordingAfterDeletion() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long joiningId = saveMember("joining");
        long workspaceId = saveWorkspace("삭제 초대 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        String invitationCode = issueInvitationCode(
                workspaceId,
                ownerId
        );

        // when
        ResultActions result = deleteWorkspace(
                Long.toString(workspaceId),
                ownerId
        );

        // then
        result.andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/invitations/" + invitationCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_INVITATION_PREVIEW_NOT_FOUND"));
        acceptInvitation(
                invitationCode,
                joiningId
        ).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_INVITATION_PREVIEW_NOT_FOUND"));
        startRecordingRequest(
                workspaceId,
                ownerId
        ).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("MEMBER의 삭제 요청은 403이고 워크스페이스를 유지한다")
    void delete_failure_member() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("팀");
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

        // when
        ResultActions result = deleteWorkspace(
                Long.toString(workspaceId),
                memberId
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_OWNER_REQUIRED"));
        assertThat(isWorkspaceDeleted(workspaceId)).isFalse();
    }

    @Test
    @DisplayName("참여하지 않은 사용자의 삭제 요청은 403이다")
    void delete_failure_nonMember() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long outsiderId = saveMember("outsider");
        long workspaceId = saveWorkspace("팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );

        // when
        ResultActions result = deleteWorkspace(
                Long.toString(workspaceId),
                outsiderId
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
        assertThat(isWorkspaceDeleted(workspaceId)).isFalse();
    }

    @Test
    @DisplayName("이미 삭제된 워크스페이스를 다시 삭제하면 404다")
    void delete_failure_alreadyDeleted() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace("팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        deleteWorkspace(
                Long.toString(workspaceId),
                ownerId
        ).andExpect(status().isNoContent());

        // when
        ResultActions result = deleteWorkspace(
                Long.toString(workspaceId),
                ownerId
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스 삭제는 404다")
    void delete_failure_notFound() throws Exception {
        // given
        long memberId = saveMember("member");

        // when
        ResultActions result = deleteWorkspace(
                "999",
                memberId
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("워크스페이스 ID가 양수가 아니면 삭제는 400이다")
    void delete_failure_nonPositiveId() throws Exception {
        // given
        long memberId = saveMember("member");

        // when
        ResultActions result = deleteWorkspace(
                "0",
                memberId
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"));
    }

    @Test
    @DisplayName("워크스페이스 ID 형식이 잘못되면 삭제는 내부 오류 없는 400이다")
    void delete_failure_invalidIdFormat() throws Exception {
        // given
        long memberId = saveMember("member");

        // when
        ResultActions result = deleteWorkspace(
                "invalid",
                memberId
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("인증 없이 CSRF 토큰만 보내면 삭제는 401이다")
    void delete_failure_unauthenticated() throws Exception {
        // given
        CsrfCredentials csrf = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                delete("/api/v1/workspaces/1").cookie(csrf.cookie())
                        .header(
                                "X-XSRF-TOKEN",
                                csrf.token()
                        )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("인증됐어도 CSRF 토큰이 없으면 삭제는 403이고 워크스페이스를 유지한다")
    void delete_failure_missingCsrf() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace("팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );

        // when
        ResultActions result = mockMvc
                .perform(delete("/api/v1/workspaces/" + workspaceId).cookie(accessTokenCookie(ownerId)));

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(isWorkspaceDeleted(workspaceId)).isFalse();
    }

    private ResultActions deleteWorkspace(
            String workspaceId,
            long memberId
    ) throws Exception {
        CsrfCredentials csrf = csrfCredentials();
        return mockMvc.perform(
                delete("/api/v1/workspaces/" + workspaceId).cookie(
                        accessTokenCookie(memberId),
                        csrf.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrf.token()
                        )
        );
    }

    private String issueInvitationCode(
            long workspaceId,
            long memberId
    ) throws Exception {
        CsrfCredentials csrf = csrfCredentials();
        MvcResult result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/invitations",
                        workspaceId
                ).cookie(
                        accessTokenCookie(memberId),
                        csrf.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrf.token()
                        )
        )
                .andExpect(status().isCreated())
                .andReturn();
        return readBody(result).get("code")
                .asText();
    }

    private ResultActions acceptInvitation(
            String credential,
            long memberId
    ) throws Exception {
        CsrfCredentials csrf = csrfCredentials();
        return mockMvc.perform(
                post("/api/v1/invitations/accept").cookie(
                        accessTokenCookie(memberId),
                        csrf.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrf.token()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credential":"%s"}
                                """.formatted(credential))
        );
    }

    private long startRecording(
            long workspaceId,
            long memberId
    ) throws Exception {
        MvcResult result = startRecordingRequest(
                workspaceId,
                memberId
        ).andExpect(status().isCreated())
                .andReturn();
        return readBody(result).get("recordingId")
                .asLong();
    }

    private ResultActions startRecordingRequest(
            long workspaceId,
            long memberId
    ) throws Exception {
        CsrfCredentials csrf = csrfCredentials();
        return mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/recordings",
                        workspaceId
                ).cookie(
                        accessTokenCookie(memberId),
                        csrf.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrf.token()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                        {"requestId":"%s","tabId":"%s","controlToken":"%s"}
                                        """.formatted(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        CONTROL_TOKEN
                                )
                        )
        );
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse()
                        .getContentAsString()
        );
    }

    private CsrfCredentials csrfCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);
        assertThat(cookie).isNotNull();
        return new CsrfCredentials(
                cookie,
                readBody(result).get("token")
                        .asText()
        );
    }

    private Cookie accessTokenCookie(long memberId) {
        return new Cookie(
                JWT_COOKIE_NAME,
                authTokenProvider.issue(
                        AuthenticatedMember.of(
                                memberId,
                                "hyunsung",
                                null
                        )
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
                        JOINED_AT.toString()
                )
                .update();
    }

    private void markLastViewed(
            long workspaceId,
            long memberId
    ) {
        jdbcClient.sql("""
                UPDATE workspace_members
                SET last_viewed = TRUE
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
                .update();
    }

    private String recordingStatus(long recordingId) {
        return jdbcClient.sql("SELECT status FROM recording_sessions WHERE id = :recordingId")
                .param(
                        "recordingId",
                        recordingId
                )
                .query(String.class)
                .single();
    }

    private boolean isWorkspaceDeleted(long workspaceId) {
        return jdbcClient.sql("SELECT deleted_at IS NOT NULL FROM workspaces WHERE id = :workspaceId")
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Boolean.class)
                .single();
    }

    private record CsrfCredentials(
            Cookie cookie,
            String token
    ) {
    }
}
