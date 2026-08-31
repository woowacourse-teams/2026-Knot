package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doReturn;

import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberRepository;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationSecrets;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import com.knot.backend.workspace.domain.WorkspaceInvitationSecretCollisionException;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkspaceInvitationServiceIntegrationTest {
    @MockitoSpyBean
    private WorkspaceInvitationSecretGenerator secretGenerator;
    private final WorkspaceInvitationService workspaceInvitationService;
    private final WorkspaceInvitationSecretProtector secretProtector;
    private final MemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceInvitationRepository workspaceInvitationRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final JdbcClient jdbcClient;

    WorkspaceInvitationServiceIntegrationTest(
            WorkspaceInvitationService workspaceInvitationService,
            WorkspaceInvitationSecretProtector secretProtector,
            MemberRepository memberRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceInvitationRepository workspaceInvitationRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            JdbcClient jdbcClient
    ) {
        this.workspaceInvitationService = workspaceInvitationService;
        this.secretProtector = secretProtector;
        this.memberRepository = memberRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceInvitationRepository = workspaceInvitationRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.jdbcClient = jdbcClient;
    }

    @DisplayName("만료된 V4 초대를 교체하면 새 활성 초대 하나를 다시 조회할 수 있다")
    @Test
    void issue_success_replacesExpiredRecoverableInvitation() {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture("만료 교체 팀");
        Instant createdAt = Instant.now()
                .minus(WorkspaceInvitation.VALIDITY_PERIOD)
                .minusSeconds(1);
        String expiredCode = uniqueValue("expired-code-");
        WorkspaceInvitation expiredInvitation = recoverableInvitation(
                fixture.workspaceId(),
                expiredCode,
                "expired-link-token",
                createdAt
        );
        workspaceInvitationRepository.save(expiredInvitation);

        // when
        WorkspaceInvitationResult issued = workspaceInvitationService.issue(
                fixture.workspaceId(),
                fixture.memberId()
        );

        // then
        WorkspaceInvitationResult found = workspaceInvitationService.get(
                fixture.workspaceId(),
                fixture.memberId()
        );
        assertThat(issued.created()).isTrue();
        assertThat(found.code()).isEqualTo(issued.code());
        assertThat(found.linkToken()).isEqualTo(issued.linkToken());
        assertThat(found.expiresAt()).isEqualTo(issued.expiresAt());
        assertThat(countInvitations(fixture.workspaceId())).isEqualTo(2);
        assertThat(countUninvalidatedInvitations(fixture.workspaceId())).isEqualTo(1);
    }

    @DisplayName("최초 발급 secret이 충돌하면 rollback한 뒤 새 secret으로 재시도한다")
    @Test
    void issue_success_retriesWhenGeneratedSecretCollides() {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture("최초 발급 충돌 복구 팀");
        WorkspaceFixture collisionFixture = createWorkspaceFixture("최초 발급 충돌 대상 팀");
        String duplicateCode = "GHJ789";
        String duplicateLinkToken = uniqueValue("issue-duplicate-link-token-");
        String retriedCode = "KMN234";
        String retriedLinkToken = uniqueValue("issue-retried-link-token-");
        workspaceInvitationRepository.save(
                recoverableInvitation(
                        collisionFixture.workspaceId(),
                        duplicateCode,
                        duplicateLinkToken,
                        Instant.now()
                )
        );
        doReturn(
                new WorkspaceInvitationSecrets(
                        duplicateCode,
                        duplicateLinkToken
                ),
                new WorkspaceInvitationSecrets(
                        retriedCode,
                        retriedLinkToken
                )
        ).when(secretGenerator)
                .generate();

        // when
        WorkspaceInvitationResult result = workspaceInvitationService.issue(
                fixture.workspaceId(),
                fixture.memberId()
        );

        // then
        assertThat(result.code()).isEqualTo(retriedCode);
        assertThat(result.linkToken()).isEqualTo(retriedLinkToken);
        assertThat(result.created()).isTrue();
        assertThat(countInvitations(fixture.workspaceId())).isEqualTo(1);
        assertThat(countUninvalidatedInvitations(fixture.workspaceId())).isEqualTo(1);
    }

    @DisplayName("secret 충돌 재시도가 모두 실패하면 기존 초대 무효화를 rollback한다")
    @Test
    void reissue_failure_rollsBackInvalidationWhenSaveFails() {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture("재발급 롤백 팀");
        WorkspaceFixture collisionFixture = createWorkspaceFixture("중복 secret 팀");
        String duplicateCode = uniqueValue("duplicate-code-");
        String duplicateLinkToken = uniqueValue("duplicate-link-token-");
        workspaceInvitationService.issue(
                fixture.workspaceId(),
                fixture.memberId()
        );
        workspaceInvitationRepository.save(
                recoverableInvitation(
                        collisionFixture.workspaceId(),
                        duplicateCode,
                        duplicateLinkToken,
                        Instant.now()
                )
        );
        doReturn(
                new WorkspaceInvitationSecrets(
                        duplicateCode,
                        duplicateLinkToken
                )
        ).when(secretGenerator)
                .generate();

        // when
        Throwable thrown = catchThrowable(
                () -> workspaceInvitationService.reissue(
                        fixture.workspaceId(),
                        fixture.memberId()
                )
        );

        // then
        assertThat(thrown).isInstanceOf(WorkspaceInvitationSecretCollisionException.class);
        assertThat(countInvitations(fixture.workspaceId())).isEqualTo(1);
        assertThat(countUninvalidatedInvitations(fixture.workspaceId())).isEqualTo(1);
    }

    @DisplayName("재발급 secret이 충돌하면 rollback한 뒤 새 secret으로 재시도한다")
    @Test
    void reissue_success_retriesWhenGeneratedSecretCollides() {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture("재발급 충돌 복구 팀");
        WorkspaceFixture collisionFixture = createWorkspaceFixture("충돌 대상 팀");
        String duplicateCode = "ABC234";
        String duplicateLinkToken = uniqueValue("duplicate-link-token-");
        String retriedCode = "DEF567";
        String retriedLinkToken = uniqueValue("retried-link-token-");
        workspaceInvitationService.issue(
                fixture.workspaceId(),
                fixture.memberId()
        );
        workspaceInvitationRepository.save(
                recoverableInvitation(
                        collisionFixture.workspaceId(),
                        duplicateCode,
                        duplicateLinkToken,
                        Instant.now()
                )
        );
        doReturn(
                new WorkspaceInvitationSecrets(
                        duplicateCode,
                        duplicateLinkToken
                ),
                new WorkspaceInvitationSecrets(
                        retriedCode,
                        retriedLinkToken
                )
        ).when(secretGenerator)
                .generate();

        // when
        WorkspaceInvitationResult result = workspaceInvitationService.reissue(
                fixture.workspaceId(),
                fixture.memberId()
        );

        // then
        assertThat(result.code()).isEqualTo(retriedCode);
        assertThat(result.linkToken()).isEqualTo(retriedLinkToken);
        assertThat(result.created()).isTrue();
        assertThat(countInvitations(fixture.workspaceId())).isEqualTo(2);
        assertThat(countUninvalidatedInvitations(fixture.workspaceId())).isEqualTo(1);
    }

    @DisplayName("동시 ensure-active 요청은 하나의 활성 초대에 수렴한다")
    @Test
    void issue_success_concurrentRequestsReturnSameInvitation() throws Exception {
        // given
        WorkspaceFixture fixture = createWorkspaceFixture("동시성 팀");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Callable<WorkspaceInvitationResult> issueInvitation = () -> {
            barrier.await();
            return workspaceInvitationService.issue(
                    fixture.workspaceId(),
                    fixture.memberId()
            );
        };

        try {
            // when
            List<WorkspaceInvitationResult> results = awaitResults(
                    executorService,
                    issueInvitation
            );

            // then
            assertThat(results).extracting(WorkspaceInvitationResult::code)
                    .containsOnly(
                            results.getFirst()
                                    .code()
                    );
            assertThat(results).extracting(WorkspaceInvitationResult::linkToken)
                    .containsOnly(
                            results.getFirst()
                                    .linkToken()
                    );
            assertThat(results).extracting(WorkspaceInvitationResult::expiresAt)
                    .containsOnly(
                            results.getFirst()
                                    .expiresAt()
                    );
            assertThat(results).extracting(WorkspaceInvitationResult::created)
                    .containsExactlyInAnyOrder(
                            true,
                            false
                    );
            assertThat(countInvitations(fixture.workspaceId())).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    private WorkspaceFixture createWorkspaceFixture(String workspaceName) {
        Instant now = Instant.now();
        Member member = memberRepository.save(
                Member.create(
                        uniqueNickname(),
                        null
                )
        );
        Workspace workspace = workspaceRepository.save(
                Workspace.create(
                        workspaceName,
                        now
                )
        );
        workspaceMemberRepository.save(
                WorkspaceMember.create(
                        workspace.getId(),
                        member.getId(),
                        WorkspaceMemberRole.MEMBER,
                        now
                )
        );
        return new WorkspaceFixture(
                workspace.getId(),
                member.getId()
        );
    }

    private WorkspaceInvitation recoverableInvitation(
            Long workspaceId,
            String code,
            String linkToken,
            Instant createdAt
    ) {
        return WorkspaceInvitation.create(
                workspaceId,
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        linkToken
                ),
                secretProtector.hash(
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        code
                ),
                secretProtector.encrypt(
                        workspaceId,
                        WorkspaceInvitationSecretKind.LINK_TOKEN,
                        linkToken
                ),
                secretProtector.encrypt(
                        workspaceId,
                        WorkspaceInvitationSecretKind.INVITE_CODE,
                        code
                ),
                createdAt
        );
    }

    private List<WorkspaceInvitationResult> awaitResults(
            ExecutorService executorService,
            Callable<WorkspaceInvitationResult> issueInvitation
    ) throws Exception {
        Future<WorkspaceInvitationResult> first = executorService.submit(issueInvitation);
        Future<WorkspaceInvitationResult> second = executorService.submit(issueInvitation);
        return List.of(
                first.get(),
                second.get()
        );
    }

    private String uniqueNickname() {
        return "member" + UUID.randomUUID()
                .toString()
                .replace(
                        "-",
                        ""
                )
                .substring(
                        0,
                        10
                );
    }

    private String uniqueValue(String prefix) {
        return prefix + UUID.randomUUID()
                .toString()
                .replace(
                        "-",
                        ""
                );
    }

    private long countInvitations(Long workspaceId) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM workspace_invitations
                WHERE workspace_id = :workspaceId
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
    }

    private long countUninvalidatedInvitations(Long workspaceId) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM workspace_invitations
                WHERE workspace_id = :workspaceId
                  AND invalidated_at IS NULL
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
    }

    private record WorkspaceFixture(
            Long workspaceId,
            long memberId
    ) {
    }
}
