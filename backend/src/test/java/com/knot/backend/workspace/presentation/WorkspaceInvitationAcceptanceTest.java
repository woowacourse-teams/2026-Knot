package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberRepository;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.WorkspaceInvitationSecretGenerator;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
class WorkspaceInvitationAcceptanceTest {
    @MockitoSpyBean
    private WorkspaceInvitationSecretGenerator secretGenerator;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final AuthTokenProvider authTokenProvider;
    private final MemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final JdbcClient jdbcClient;

    WorkspaceInvitationAcceptanceTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            AuthTokenProvider authTokenProvider,
            MemberRepository memberRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            JdbcClient jdbcClient
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.authTokenProvider = authTokenProvider;
        this.memberRepository = memberRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.jdbcClient = jdbcClient;
    }

    @DisplayName("워크스페이스 멤버가 최초 초대를 발급하면 201과 원문을 반환한다")
    @Test
    void issue_success_created() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(true);

        // when
        ResultActions result = performIssue(fixture);

        // then
        result.andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                "/workspaces/" + fixture.workspaceId() + "/invitation"
                        )
                )
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.linkToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @DisplayName("초대 공유 화면을 다시 조회하면 최초 발급한 같은 원문을 반환한다")
    @Test
    void get_success_returnsSameInvitation() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(true);
        JsonNode issued = responseBody(performIssue(fixture));

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}/invitation",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.code").value(
                                issued.get("code")
                                        .asText()
                        )
                )
                .andExpect(
                        jsonPath("$.linkToken").value(
                                issued.get("linkToken")
                                        .asText()
                        )
                )
                .andExpect(
                        jsonPath("$.expiresAt").value(
                                issued.get("expiresAt")
                                        .asText()
                        )
                );
    }

    @DisplayName("활성 초대가 있을 때 일반 발급을 다시 요청하면 200과 같은 원문을 반환한다")
    @Test
    void issue_success_idempotent() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(true);
        JsonNode firstIssued = responseBody(performIssue(fixture));

        // when
        ResultActions result = performIssue(fixture);

        // then
        result.andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(
                        jsonPath("$.code").value(
                                firstIssued.get("code")
                                        .asText()
                        )
                )
                .andExpect(
                        jsonPath("$.linkToken").value(
                                firstIssued.get("linkToken")
                                        .asText()
                        )
                )
                .andExpect(
                        jsonPath("$.expiresAt").value(
                                firstIssued.get("expiresAt")
                                        .asText()
                        )
                );
    }

    @DisplayName("명시적 재발급은 기존 초대를 무효화하고 다른 원문을 반환한다")
    @Test
    void reissue_success_replacesInvitation() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(true);
        JsonNode firstIssued = responseBody(performIssue(fixture));

        // when
        MvcResult result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations/reissue",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
                        .with(csrf())
        )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                "/workspaces/" + fixture.workspaceId() + "/invitation"
                        )
                )
                .andReturn();

        // then
        JsonNode reissued = objectMapper.readTree(
                result.getResponse()
                        .getContentAsString()
        );
        assertThat(
                reissued.get("code")
                        .asText()
        ).isNotEqualTo(
                firstIssued.get("code")
                        .asText()
        );
        assertThat(
                reissued.get("linkToken")
                        .asText()
        ).isNotEqualTo(
                firstIssued.get("linkToken")
                        .asText()
        );
        assertThat(countInvitations(fixture.workspaceId())).isEqualTo(2);
        assertThat(countUninvalidatedInvitations(fixture.workspaceId())).isEqualTo(1);
    }

    @DisplayName("인증되지 않은 초대 발급 요청은 401을 반환한다")
    @Test
    void issue_failure_unauthenticated() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations",
                        1L
                ).with(csrf())
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @DisplayName("인증과 CSRF 토큰이 모두 없는 초대 발급 요청은 403을 반환한다")
    @Test
    void issue_failure_missingAuthenticationAndCsrf() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations",
                        1L
                )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @DisplayName("인증과 CSRF 토큰이 모두 없는 초대 재발급 요청은 403을 반환한다")
    @Test
    void reissue_failure_missingAuthenticationAndCsrf() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations/reissue",
                        1L
                )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @DisplayName("인증됐지만 CSRF 토큰이 없는 초대 발급 요청은 403을 반환한다")
    @Test
    void issue_failure_missingCsrf() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(true);

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @DisplayName("인증되지 않은 초대 재발급 요청은 401을 반환한다")
    @Test
    void reissue_failure_unauthenticated() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations/reissue",
                        1L
                ).with(csrf())
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @DisplayName("인증됐지만 CSRF 토큰이 없는 초대 재발급 요청은 403을 반환한다")
    @Test
    void reissue_failure_missingCsrf() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(true);

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations/reissue",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @DisplayName("워크스페이스 멤버가 아닌 사용자의 초대 발급 요청은 403을 반환한다")
    @Test
    void issue_failure_nonMember() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(false);

        // when
        ResultActions result = performIssue(fixture);

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @DisplayName("존재하지 않는 워크스페이스의 초대 발급 요청은 404를 반환한다")
    @Test
    void issue_failure_workspaceNotFound() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(false);
        Long missingWorkspaceId = Long.MAX_VALUE;

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations",
                        missingWorkspaceId
                ).cookie(authenticatedCookie(fixture.member()))
                        .with(csrf())
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @DisplayName("양수가 아닌 Workspace ID의 초대 발급 요청은 400을 반환한다")
    @Test
    void issue_failure_invalidWorkspaceId() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(false);

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations",
                        0
                ).cookie(authenticatedCookie(fixture.member()))
                        .with(csrf())
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"));
    }

    @DisplayName("양수가 아닌 Workspace ID의 초대 조회 요청은 400을 반환한다")
    @Test
    void get_failure_invalidWorkspaceId() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(false);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}/invitation",
                        0
                ).cookie(authenticatedCookie(fixture.member()))
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"));
    }

    @DisplayName("인증되지 않은 초대 조회 요청은 401을 반환한다")
    @Test
    void get_failure_unauthenticated() throws Exception {
        // given

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}/invitation",
                        1L
                )
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @DisplayName("워크스페이스 멤버가 아닌 사용자의 초대 조회 요청은 403을 반환한다")
    @Test
    void get_failure_nonMember() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(false);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}/invitation",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @DisplayName("존재하지 않는 워크스페이스의 초대 조회 요청은 404를 반환한다")
    @Test
    void get_failure_workspaceNotFound() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(false);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}/invitation",
                        Long.MAX_VALUE
                ).cookie(authenticatedCookie(fixture.member()))
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @DisplayName("활성 초대가 없으면 조회 요청에 404를 반환한다")
    @Test
    void get_failure_invitationNotFound() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(true);

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}/invitation",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_INVITATION_NOT_FOUND"));
    }

    @DisplayName("복원할 암호문이 없는 V3 활성 초대는 민감정보 없는 500을 반환한다")
    @Test
    void get_failure_legacyInvitation() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(true);
        String linkTokenHash = uniqueValue("legacy-link-");
        String inviteCodeHash = uniqueValue("legacy-code-");
        insertLegacyInvitation(
                fixture.workspaceId(),
                linkTokenHash,
                inviteCodeHash
        );

        // when
        MvcResult result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}/invitation",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
        )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED"))
                .andReturn();

        // then
        assertThat(
                result.getResponse()
                        .getContentAsString()
        ).doesNotContain(
                linkTokenHash,
                inviteCodeHash
        );
        assertThat(countUninvalidatedInvitations(fixture.workspaceId())).isEqualTo(1);
    }

    @DisplayName("복원할 암호문이 없는 V3 초대도 명시적 재발급으로 교체한다")
    @Test
    void reissue_success_recoversLegacyInvitation() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(true);
        insertLegacyInvitation(
                fixture.workspaceId(),
                uniqueValue("legacy-link-"),
                uniqueValue("legacy-code-")
        );

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations/reissue",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
                        .with(csrf())
        );

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.linkToken").isNotEmpty());
        assertThat(countInvitations(fixture.workspaceId())).isEqualTo(2);
        assertThat(countUninvalidatedInvitations(fixture.workspaceId())).isEqualTo(1);
    }

    @DisplayName("양수가 아닌 Workspace ID의 초대 재발급 요청은 400을 반환한다")
    @Test
    void reissue_failure_invalidWorkspaceId() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(false);

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations/reissue",
                        0
                ).cookie(authenticatedCookie(fixture.member()))
                        .with(csrf())
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"));
    }

    @DisplayName("워크스페이스 멤버가 아닌 사용자의 초대 재발급 요청은 403을 반환한다")
    @Test
    void reissue_failure_nonMember() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(false);

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations/reissue",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
                        .with(csrf())
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @DisplayName("존재하지 않는 워크스페이스의 초대 재발급 요청은 404를 반환한다")
    @Test
    void reissue_failure_workspaceNotFound() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(false);

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations/reissue",
                        Long.MAX_VALUE
                ).cookie(authenticatedCookie(fixture.member()))
                        .with(csrf())
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @DisplayName("초대 secret 생성에 실패한 재발급 요청은 민감정보 없는 500을 반환한다")
    @Test
    void reissue_failure_secretGeneration() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture(true);
        doThrow(new WorkspaceException(WorkspaceErrorCode.WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED))
                .when(secretGenerator)
                .generate();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations/reissue",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
                        .with(csrf())
        );

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("WORKSPACE_INVITATION_SECRET_RECOVERY_FAILED"));
    }

    private WorkspaceFixture createWorkspaceFixture(boolean memberOfWorkspace) {
        Instant now = Instant.now()
                .minusSeconds(1);
        Member member = memberRepository.save(
                Member.create(
                        uniqueValue("member"),
                        null
                )
        );
        Workspace workspace = workspaceRepository.save(
                Workspace.create(
                        "초대 테스트 팀",
                        now
                )
        );
        if (memberOfWorkspace) {
            workspaceMemberRepository.save(
                    WorkspaceMember.create(
                            workspace.getId(),
                            member.getId(),
                            WorkspaceMemberRole.MEMBER,
                            now
                    )
            );
        }
        return new WorkspaceFixture(
                workspace.getId(),
                AuthenticatedMember.of(
                        member.getId(),
                        member.getNickname(),
                        member.getProfileImageUrl()
                )
        );
    }

    private ResultActions performIssue(WorkspaceFixture fixture) throws Exception {
        return mockMvc.perform(
                post(
                        "/workspaces/{workspaceId}/invitations",
                        fixture.workspaceId()
                ).cookie(authenticatedCookie(fixture.member()))
                        .with(csrf())
        );
    }

    private JsonNode responseBody(ResultActions result) throws Exception {
        return objectMapper.readTree(
                result.andReturn()
                        .getResponse()
                        .getContentAsString()
        );
    }

    private Cookie authenticatedCookie(AuthenticatedMember member) {
        return new Cookie(
                "KNOT_ACCESS_TOKEN",
                authTokenProvider.issue(member)
        );
    }

    private void insertLegacyInvitation(
            Long workspaceId,
            String linkTokenHash,
            String inviteCodeHash
    ) {
        Instant createdAt = Instant.now()
                .minusSeconds(1);
        jdbcClient.sql("""
                INSERT INTO workspace_invitations (
                    workspace_id,
                    link_token_hash,
                    invite_code_hash,
                    expires_at,
                    created_at
                ) VALUES (
                    :workspaceId,
                    :linkTokenHash,
                    :inviteCodeHash,
                    :expiresAt,
                    :createdAt
                )
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "linkTokenHash",
                        linkTokenHash
                )
                .param(
                        "inviteCodeHash",
                        inviteCodeHash
                )
                .param(
                        "expiresAt",
                        toOffsetDateTime(createdAt.plus(WorkspaceInvitation.VALIDITY_PERIOD))
                )
                .param(
                        "createdAt",
                        toOffsetDateTime(createdAt)
                )
                .update();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private String uniqueValue(String prefix) {
        return prefix + UUID.randomUUID()
                .toString()
                .replace(
                        "-",
                        ""
                )
                .substring(
                        0,
                        12
                );
    }

    private long countInvitations(Long workspaceId) {
        return countRows(
                workspaceId,
                ""
        );
    }

    private long countUninvalidatedInvitations(Long workspaceId) {
        return countRows(
                workspaceId,
                "AND invalidated_at IS NULL"
        );
    }

    private long countRows(
            Long workspaceId,
            String condition
    ) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM workspace_invitations
                WHERE workspace_id = :workspaceId
                %s
                """.formatted(condition))
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
    }

    private record WorkspaceFixture(
            Long workspaceId,
            AuthenticatedMember member
    ) {
    }
}
