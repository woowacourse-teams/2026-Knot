package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.testsupport.NotionOAuthTestProperties;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.ContentSourceAuthorizationClient;
import com.knot.backend.workspace.application.dto.result.AuthorizedContentSource;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationOwnerType;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("acceptance")
@Import({TestcontainersConfiguration.class,
        NotionOAuthAcceptanceTest.ContentSourceAuthorizationClientTestConfiguration.class})
@TestApplicationProperties
@NotionOAuthTestProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotionOAuthAcceptanceTest {
    private static final String JWT_COOKIE_NAME = "KNOT_ACCESS_TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String REDIRECT_BASE_URI = "https://knoted.kr";
    private static final String FAILURE_FALLBACK_URI = REDIRECT_BASE_URI + "/workspace?result=failed";
    private static final String OAUTH_CODE = "oauth-code";
    private static final String ACCESS_CREDENTIAL = "notion-access-token";
    private static final String REFRESH_CREDENTIAL = "notion-refresh-token";
    private static final String CREATED_AT_VALUE = "2026-08-31T00:00:00Z";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;
    private final ContentSourceAuthorizationClient notionOAuthClient;

    NotionOAuthAcceptanceTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            AuthTokenProvider authTokenProvider,
            JdbcClient jdbcClient,
            ContentSourceAuthorizationClient notionOAuthClient
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.authTokenProvider = authTokenProvider;
        this.jdbcClient = jdbcClient;
        this.notionOAuthClient = notionOAuthClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient.sql("""
                TRUNCATE TABLE content_source_connections, content_source_authorizations,
                    workspace_members, workspaces, oauth_identities, members
                RESTART IDENTITY CASCADE
                """)
                .update();
        reset(notionOAuthClient);
        when(notionOAuthClient.provider()).thenReturn(ContentSourceProvider.NOTION);
    }

    @DisplayName("OWNER가 CSRF 토큰으로 Notion OAuth를 시작하면 201과 authorization URL을 반환한다")
    @Test
    void start_success_ownerWithCsrf() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                ownerId,
                "OWNER"
        );
        CsrfCredentials csrfCredentials = csrfCredentials();
        stubAuthorizationUri();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/notion-oauth-authorizations",
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
        result.andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(
                        jsonPath("$.authorizationUrl")
                                .value(org.hamcrest.Matchers.startsWith("https://api.notion.test/oauth?state="))
                );
        assertThat(countRows("content_source_authorizations")).isEqualTo(1);
    }

    @DisplayName("인증되지 않은 Notion OAuth 시작 요청은 401을 반환한다")
    @Test
    void start_failure_unauthenticated() throws Exception {
        // given
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/notion-oauth-authorizations",
                        1L
                ).cookie(csrfCredentials.cookie())
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @DisplayName("CSRF 토큰이 없는 Notion OAuth 시작 요청은 403을 반환한다")
    @Test
    void start_failure_missingCsrf() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                ownerId,
                "OWNER"
        );

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/notion-oauth-authorizations",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                ownerId,
                                "owner"
                        )
                )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(countRows("content_source_authorizations")).isZero();
    }

    @DisplayName("MEMBER의 Notion OAuth 시작 요청은 403을 반환한다")
    @Test
    void start_failure_memberRole() throws Exception {
        // given
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                memberId,
                "MEMBER"
        );
        CsrfCredentials csrfCredentials = csrfCredentials();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/notion-oauth-authorizations",
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
        assertThat(countRows("content_source_authorizations")).isZero();
    }

    @DisplayName("Notion OAuth callback 성공은 connection을 저장하고 성공 화면으로 redirect한다")
    @Test
    void callback_success_connectsWorkspace() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                ownerId,
                "OWNER"
        );
        String state = startAuthorization(
                workspaceId,
                ownerId,
                "owner"
        );
        when(
                notionOAuthClient.exchange(
                        eq(ContentSourceProvider.NOTION),
                        eq(OAUTH_CODE),
                        any(URI.class)
                )
        ).thenReturn(token("notion-workspace-id"));

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/notion/oauth/callback").param(
                        "code",
                        OAUTH_CODE
                )
                        .param(
                                "state",
                                state
                        )
        );

        // then
        result.andExpect(status().isSeeOther())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                successRedirectUri(workspaceId)
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                );
        assertThat(countRows("content_source_connections")).isEqualTo(1);
        assertThat(
                singleText(
                        "content_source_connections",
                        "access_credential_ciphertext"
                )
        ).doesNotContain(ACCESS_CREDENTIAL);
        assertThat(
                singleText(
                        "content_source_connections",
                        "refresh_credential_ciphertext"
                )
        ).doesNotContain(REFRESH_CREDENTIAL);
        assertThat(
                singleText(
                        "content_source_authorizations",
                        "state_hash"
                )
        ).doesNotContain(state);
    }

    @DisplayName("Notion OAuth callback cancel은 기존 connection을 보존하고 실패 화면으로 redirect한다")
    @Test
    void callback_failure_cancelPreservesConnection() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                ownerId,
                "OWNER"
        );
        saveConnection(
                workspaceId,
                ownerId,
                "existing-notion-workspace"
        );
        String state = startAuthorization(
                workspaceId,
                ownerId,
                "owner"
        );

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/notion/oauth/callback").param(
                        "state",
                        state
                )
                        .param(
                                "error",
                                "access_denied"
                        )
        );

        // then
        result.andExpect(status().isSeeOther())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                failureRedirectUri(workspaceId)
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                );
        assertThat(
                singleText(
                        "content_source_connections",
                        "external_source_id"
                )
        ).isEqualTo("existing-notion-workspace");
        assertThat(countConsumedAuthorizations()).isOne();
    }

    @DisplayName("Notion OAuth callback invalid state는 기존 connection을 보존한다")
    @Test
    void callback_failure_invalidStatePreservesConnection() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                ownerId,
                "OWNER"
        );
        saveConnection(
                workspaceId,
                ownerId,
                "existing-notion-workspace"
        );

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/notion/oauth/callback").param(
                        "code",
                        OAUTH_CODE
                )
                        .param(
                                "state",
                                "invalid-state"
                        )
        );

        // then
        result.andExpect(status().isSeeOther())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                FAILURE_FALLBACK_URI
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                );
        assertThat(
                singleText(
                        "content_source_connections",
                        "external_source_id"
                )
        ).isEqualTo("existing-notion-workspace");
    }

    @DisplayName("Notion OAuth callback replay는 기존 connection을 보존하고 실패 화면으로 redirect한다")
    @Test
    void callback_failure_replayPreservesConnection() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                ownerId,
                "OWNER"
        );
        String state = startAuthorization(
                workspaceId,
                ownerId,
                "owner"
        );
        when(
                notionOAuthClient.exchange(
                        eq(ContentSourceProvider.NOTION),
                        eq(OAUTH_CODE),
                        any(URI.class)
                )
        ).thenReturn(token("first-notion-workspace"));
        completeCallback(
                OAUTH_CODE,
                state,
                workspaceId
        );

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/notion/oauth/callback").param(
                        "code",
                        OAUTH_CODE
                )
                        .param(
                                "state",
                                state
                        )
        );

        // then
        result.andExpect(status().isSeeOther())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                FAILURE_FALLBACK_URI
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                );
        assertThat(
                singleText(
                        "content_source_connections",
                        "external_source_id"
                )
        ).isEqualTo("first-notion-workspace");
    }

    @DisplayName("Notion provider 실패는 기존 connection을 보존하고 실패 화면으로 redirect한다")
    @Test
    void callback_failure_providerFailurePreservesConnection() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                ownerId,
                "OWNER"
        );
        saveConnection(
                workspaceId,
                ownerId,
                "existing-notion-workspace"
        );
        String state = startAuthorization(
                workspaceId,
                ownerId,
                "owner"
        );
        when(
                notionOAuthClient.exchange(
                        eq(ContentSourceProvider.NOTION),
                        eq(OAUTH_CODE),
                        any(URI.class)
                )
        ).thenThrow(new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_AUTHORIZATION_FAILED));

        // when
        ResultActions result = mockMvc.perform(
                get("/api/v1/notion/oauth/callback").param(
                        "code",
                        OAUTH_CODE
                )
                        .param(
                                "state",
                                state
                        )
        );

        // then
        result.andExpect(status().isSeeOther())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                failureRedirectUri(workspaceId)
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                );
        assertThat(
                singleText(
                        "content_source_connections",
                        "external_source_id"
                )
        ).isEqualTo("existing-notion-workspace");
        assertThat(countConsumedAuthorizations()).isOne();
    }

    @DisplayName("워크스페이스 MEMBER는 Notion connection 상태를 조회할 수 있다")
    @Test
    void status_success_memberAccess() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace(
                "Knot 팀",
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
                ownerId,
                "notion-workspace-id"
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-connection",
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
                .andExpect(jsonPath("$.status").value("CONNECTED"));
    }

    @DisplayName("connection이 없는 워크스페이스는 NOT_CONNECTED 상태를 반환한다")
    @Test
    void status_success_notConnected() throws Exception {
        // given
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                memberId,
                "OWNER"
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-connection",
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
                .andExpect(jsonPath("$.status").value("NOT_CONNECTED"));
    }

    @DisplayName("연결 승인자가 현재 OWNER이면 CONNECTED 상태를 반환한다")
    @Test
    void status_success_connected() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                ownerId,
                "OWNER"
        );
        saveConnection(
                workspaceId,
                ownerId,
                "notion-workspace-id"
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-connection",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                ownerId,
                                "owner"
                        )
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"));
    }

    @DisplayName("연결 승인자가 더 이상 OWNER가 아니면 REAUTH_REQUIRED 상태를 반환한다")
    @Test
    void status_success_reauthRequired() throws Exception {
        // given
        long authorizingMemberId = saveMember("authorizing-member");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                authorizingMemberId,
                "MEMBER"
        );
        saveConnection(
                workspaceId,
                authorizingMemberId,
                "notion-workspace-id"
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-connection",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                authorizingMemberId,
                                "authorizing-member"
                        )
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REAUTH_REQUIRED"));
    }

    @DisplayName("비멤버의 Notion connection 상태 조회는 connection을 노출하지 않는다")
    @Test
    void status_failure_nonMemberIsolation() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long outsiderId = saveMember("outsider");
        long workspaceId = saveWorkspace(
                "Knot 팀",
                ownerId,
                "OWNER"
        );
        saveConnection(
                workspaceId,
                ownerId,
                "private-notion-workspace"
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-connection",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                outsiderId,
                                "outsider"
                        )
                )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    private String startAuthorization(
            long workspaceId,
            long ownerId,
            String nickname
    ) throws Exception {
        CsrfCredentials csrfCredentials = csrfCredentials();
        stubAuthorizationUri();
        MvcResult result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/notion-oauth-authorizations",
                        workspaceId
                ).cookie(
                        accessTokenCookie(
                                ownerId,
                                nickname
                        ),
                        csrfCredentials.cookie()
                )
                        .header(
                                "X-XSRF-TOKEN",
                                csrfCredentials.token()
                        )
        )
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode responseBody = objectMapper.readTree(
                result.getResponse()
                        .getContentAsString()
        );
        return URI.create(
                responseBody.get("authorizationUrl")
                        .asText()
        )
                .getQuery()
                .substring("state=".length());
    }

    private void completeCallback(
            String code,
            String state,
            long workspaceId
    ) throws Exception {
        mockMvc.perform(
                get("/api/v1/notion/oauth/callback").param(
                        "code",
                        code
                )
                        .param(
                                "state",
                                state
                        )
        )
                .andExpect(status().isSeeOther())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                successRedirectUri(workspaceId)
                        )
                );
    }

    private String successRedirectUri(long workspaceId) {
        return REDIRECT_BASE_URI + "/workspace/" + workspaceId + "/notion-connection?result=connected";
    }

    private String failureRedirectUri(long workspaceId) {
        return REDIRECT_BASE_URI + "/workspace/" + workspaceId + "/notion-connection?result=failed";
    }

    private void stubAuthorizationUri() {
        when(
                notionOAuthClient.createAuthorizationUri(
                        eq(ContentSourceProvider.NOTION),
                        anyString(),
                        any(URI.class)
                )
        ).thenAnswer(invocation -> URI.create("https://api.notion.test/oauth?state=" + invocation.getArgument(1)));
    }

    private AuthorizedContentSource token(String notionWorkspaceId) {
        return new AuthorizedContentSource(
                ContentSourceProvider.NOTION,
                ACCESS_CREDENTIAL,
                REFRESH_CREDENTIAL,
                notionWorkspaceId,
                "Knot Notion",
                null,
                "bot-id",
                ContentSourceAuthorizationOwnerType.USER,
                "notion-owner-user-id",
                null,
                null
        );
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

    private long saveWorkspace(
            String name,
            long memberId,
            String role
    ) {
        long workspaceId = jdbcClient.sql("""
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
                        createdAt()
                )
                .query(Long.class)
                .single();
        saveWorkspaceMember(
                workspaceId,
                memberId,
                role
        );
        return workspaceId;
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
                        createdAt()
                )
                .update();
    }

    private void saveConnection(
            long workspaceId,
            long authorizingMemberId,
            String notionWorkspaceId
    ) {
        jdbcClient.sql("""
                INSERT INTO content_source_connections (
                    workspace_id, provider, access_credential_ciphertext, refresh_credential_ciphertext,
                    external_source_id, external_source_name, provider_connection_id,
                    authorization_owner_type, authorization_owner_id,
                    authorizing_member_id, created_at, updated_at
                )
                VALUES (
                    :workspaceId, 'NOTION', 'existing-access-ciphertext', 'existing-refresh-ciphertext',
                    :notionWorkspaceId, 'Existing Notion', 'existing-bot-id',
                    'USER', 'notion-owner-user-id',
                    :authorizingMemberId, :createdAt, :createdAt
                )
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "notionWorkspaceId",
                        notionWorkspaceId
                )
                .param(
                        "authorizingMemberId",
                        authorizingMemberId
                )
                .param(
                        "createdAt",
                        createdAt()
                )
                .update();
    }

    private OffsetDateTime createdAt() {
        return OffsetDateTime.parse(CREATED_AT_VALUE);
    }

    private String singleText(
            String tableName,
            String columnName
    ) {
        return jdbcClient.sql("SELECT " + columnName + " FROM " + tableName)
                .query(String.class)
                .single();
    }

    private int countRows(String tableName) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + tableName)
                .query(Integer.class)
                .single();
    }

    private int countConsumedAuthorizations() {
        return jdbcClient.sql("SELECT COUNT(*) FROM content_source_authorizations WHERE consumed_at IS NOT NULL")
                .query(Integer.class)
                .single();
    }

    private record CsrfCredentials(
            Cookie cookie,
            String token
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContentSourceAuthorizationClientTestConfiguration {

        @Bean
        @Primary
        ContentSourceAuthorizationClient testContentSourceAuthorizationClient() {
            return mock(ContentSourceAuthorizationClient.class);
        }
    }
}
