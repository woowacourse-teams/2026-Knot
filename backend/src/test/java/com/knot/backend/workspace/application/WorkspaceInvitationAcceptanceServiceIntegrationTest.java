package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberRepository;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationAcceptanceResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkspaceInvitationAcceptanceServiceIntegrationTest {
    private final WorkspaceInvitationAcceptanceService acceptanceService;
    private final WorkspaceInvitationService invitationService;
    private final MemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final JdbcClient jdbcClient;

    WorkspaceInvitationAcceptanceServiceIntegrationTest(
            WorkspaceInvitationAcceptanceService acceptanceService,
            WorkspaceInvitationService invitationService,
            MemberRepository memberRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            JdbcClient jdbcClient
    ) {
        this.acceptanceService = acceptanceService;
        this.invitationService = invitationService;
        this.memberRepository = memberRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.jdbcClient = jdbcClient;
    }

    @DisplayName("같은 사용자의 동시 초대 참여는 멤버십 하나와 201·200 결과로 수렴한다")
    @Test
    void accept_success_concurrentSameMemberRequestsConverge() throws Exception {
        // given
        InvitationFixture fixture = createInvitationFixture("동시 중복 참여 팀");
        Member joiningMember = createMember();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Callable<WorkspaceInvitationAcceptanceResult> acceptInvitation = () -> {
            barrier.await(
                    5,
                    TimeUnit.SECONDS
            );
            return acceptanceService.accept(
                    fixture.invitation()
                            .code(),
                    uniqueRemoteAddress(),
                    joiningMember.getId()
            );
        };

        try {
            // when
            List<WorkspaceInvitationAcceptanceResult> results = awaitAcceptanceResults(
                    executorService,
                    acceptInvitation
            );

            // then
            assertThat(results).extracting(WorkspaceInvitationAcceptanceResult::created)
                    .containsExactlyInAnyOrder(
                            true,
                            false
                    );
            assertThat(
                    countMemberships(
                            fixture.workspaceId(),
                            joiningMember.getId()
                    )
            ).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @DisplayName("서로 다른 두 사용자는 같은 초대로 각각 MEMBER 멤버십을 만든다")
    @Test
    void accept_success_concurrentDifferentMembersKeepInvitationValid() throws Exception {
        // given
        InvitationFixture fixture = createInvitationFixture("동시 다중 참여 팀");
        Member firstMember = createMember();
        Member secondMember = createMember();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Callable<WorkspaceInvitationAcceptanceResult> firstAcceptance = () -> {
            barrier.await(
                    5,
                    TimeUnit.SECONDS
            );
            return acceptanceService.accept(
                    fixture.invitation()
                            .linkToken(),
                    uniqueRemoteAddress(),
                    firstMember.getId()
            );
        };
        Callable<WorkspaceInvitationAcceptanceResult> secondAcceptance = () -> {
            barrier.await(
                    5,
                    TimeUnit.SECONDS
            );
            return acceptanceService.accept(
                    fixture.invitation()
                            .linkToken(),
                    uniqueRemoteAddress(),
                    secondMember.getId()
            );
        };

        try {
            // when
            List<WorkspaceInvitationAcceptanceResult> results = awaitAcceptanceResults(
                    executorService,
                    firstAcceptance,
                    secondAcceptance
            );

            // then
            assertThat(results).extracting(WorkspaceInvitationAcceptanceResult::created)
                    .containsOnly(true);
            assertThat(
                    countMemberships(
                            fixture.workspaceId(),
                            firstMember.getId()
                    )
            ).isEqualTo(1);
            assertThat(
                    countMemberships(
                            fixture.workspaceId(),
                            secondMember.getId()
                    )
            ).isEqualTo(1);
            assertThat(countUninvalidatedInvitations(fixture.workspaceId())).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @DisplayName("초대 참여와 재발급 경합은 참여 성공 또는 통합 404 중 하나로 끝난다")
    @Test
    void accept_successOrNotFound_whenRacingWithReissue() throws Exception {
        // given
        InvitationFixture fixture = createInvitationFixture("참여 재발급 경합 팀");
        Member joiningMember = createMember();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Callable<AcceptanceRaceOutcome> acceptInvitation = () -> {
            barrier.await(
                    5,
                    TimeUnit.SECONDS
            );
            return acceptanceOutcome(
                    fixture.invitation()
                            .code(),
                    joiningMember.getId()
            );
        };
        Callable<WorkspaceInvitationResult> reissueInvitation = () -> {
            barrier.await(
                    5,
                    TimeUnit.SECONDS
            );
            return invitationService.reissue(
                    fixture.workspaceId(),
                    fixture.ownerMemberId()
            );
        };

        try {
            // when
            AcceptanceRaceResult raceResult = awaitRaceResult(
                    executorService,
                    acceptInvitation,
                    reissueInvitation
            );

            // then
            assertThat(
                    raceResult.reissuedInvitation()
                            .created()
            ).isTrue();
            assertThat(
                    raceResult.acceptanceOutcome()
                            .isAccepted()
                            || raceResult.acceptanceOutcome()
                                    .errorCode() == WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_NOT_FOUND
            ).isTrue();
            long expectedMembershipCount = raceResult.acceptanceOutcome()
                    .isAccepted() ? 1L : 0L;
            assertThat(
                    countMemberships(
                            fixture.workspaceId(),
                            joiningMember.getId()
                    )
            ).isEqualTo(expectedMembershipCount);
            assertThat(countInvitations(fixture.workspaceId())).isEqualTo(2);
            assertThat(countUninvalidatedInvitations(fixture.workspaceId())).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @DisplayName("멤버십 저장이 실패하면 초대와 기존 멤버십 상태를 함께 보존한다")
    @Test
    void accept_failure_rollsBackWhenMembershipSaveFails() {
        // given
        InvitationFixture fixture = createInvitationFixture("참여 롤백 팀");
        long missingMemberId = Long.MAX_VALUE;
        List<String> invitationSnapshot = invitationSnapshot(fixture.workspaceId());
        long membershipCount = countAllMemberships(fixture.workspaceId());

        // when
        Throwable thrown = catchThrowable(
                () -> acceptanceService.accept(
                        fixture.invitation()
                                .linkToken(),
                        uniqueRemoteAddress(),
                        missingMemberId
                )
        );

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countAllMemberships(fixture.workspaceId())).isEqualTo(membershipCount);
        assertThat(invitationSnapshot(fixture.workspaceId())).isEqualTo(invitationSnapshot);
    }

    private InvitationFixture createInvitationFixture(String workspaceName) {
        Instant now = Instant.now()
                .minusSeconds(1);
        Member owner = createMember();
        Workspace workspace = workspaceRepository.save(
                Workspace.create(
                        workspaceName,
                        now
                )
        );
        workspaceMemberRepository.save(
                WorkspaceMember.create(
                        workspace.getId(),
                        owner.getId(),
                        WorkspaceMemberRole.OWNER,
                        now
                )
        );
        WorkspaceInvitationResult invitation = invitationService.issue(
                workspace.getId(),
                owner.getId()
        );
        return new InvitationFixture(
                workspace.getId(),
                owner.getId(),
                invitation
        );
    }

    private Member createMember() {
        return memberRepository.save(
                Member.create(
                        uniqueNickname(),
                        null
                )
        );
    }

    private List<WorkspaceInvitationAcceptanceResult> awaitAcceptanceResults(
            ExecutorService executorService,
            Callable<WorkspaceInvitationAcceptanceResult> acceptance
    ) throws Exception {
        return awaitAcceptanceResults(
                executorService,
                acceptance,
                acceptance
        );
    }

    private List<WorkspaceInvitationAcceptanceResult> awaitAcceptanceResults(
            ExecutorService executorService,
            Callable<WorkspaceInvitationAcceptanceResult> firstAcceptance,
            Callable<WorkspaceInvitationAcceptanceResult> secondAcceptance
    ) throws Exception {
        Future<WorkspaceInvitationAcceptanceResult> first = executorService.submit(firstAcceptance);
        Future<WorkspaceInvitationAcceptanceResult> second = executorService.submit(secondAcceptance);
        return List.of(
                first.get(
                        10,
                        TimeUnit.SECONDS
                ),
                second.get(
                        10,
                        TimeUnit.SECONDS
                )
        );
    }

    private AcceptanceRaceResult awaitRaceResult(
            ExecutorService executorService,
            Callable<AcceptanceRaceOutcome> acceptance,
            Callable<WorkspaceInvitationResult> reissue
    ) throws Exception {
        Future<AcceptanceRaceOutcome> acceptanceFuture = executorService.submit(acceptance);
        Future<WorkspaceInvitationResult> reissueFuture = executorService.submit(reissue);
        return new AcceptanceRaceResult(
                acceptanceFuture.get(
                        10,
                        TimeUnit.SECONDS
                ),
                reissueFuture.get(
                        10,
                        TimeUnit.SECONDS
                )
        );
    }

    private AcceptanceRaceOutcome acceptanceOutcome(
            String credential,
            long memberId
    ) {
        try {
            return AcceptanceRaceOutcome.accepted(
                    acceptanceService.accept(
                            credential,
                            uniqueRemoteAddress(),
                            memberId
                    )
            );
        } catch (WorkspaceException exception) {
            return AcceptanceRaceOutcome.rejected((WorkspaceErrorCode) exception.getErrorCode());
        }
    }

    private long countMemberships(
            Long workspaceId,
            Long memberId
    ) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM workspace_members
                WHERE workspace_id = :workspaceId
                  AND member_id = :memberId
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

    private long countAllMemberships(Long workspaceId) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM workspace_members
                WHERE workspace_id = :workspaceId
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
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

    private List<String> invitationSnapshot(Long workspaceId) {
        return jdbcClient.sql("""
                SELECT to_jsonb(invitation)::text
                FROM workspace_invitations invitation
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

    private String uniqueRemoteAddress() {
        return "test-" + UUID.randomUUID();
    }

    private record InvitationFixture(
            Long workspaceId,
            long ownerMemberId,
            WorkspaceInvitationResult invitation
    ) {
    }

    private record AcceptanceRaceResult(
            AcceptanceRaceOutcome acceptanceOutcome,
            WorkspaceInvitationResult reissuedInvitation
    ) {
    }

    private record AcceptanceRaceOutcome(
            WorkspaceInvitationAcceptanceResult result,
            WorkspaceErrorCode errorCode
    ) {

        private static AcceptanceRaceOutcome accepted(WorkspaceInvitationAcceptanceResult result) {
            return new AcceptanceRaceOutcome(
                    result,
                    null
            );
        }

        private static AcceptanceRaceOutcome rejected(WorkspaceErrorCode errorCode) {
            return new AcceptanceRaceOutcome(
                    null,
                    errorCode
            );
        }

        private boolean isAccepted() {
            return result != null;
        }
    }
}
