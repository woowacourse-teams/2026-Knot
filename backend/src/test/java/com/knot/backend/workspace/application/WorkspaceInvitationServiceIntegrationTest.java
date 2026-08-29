package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberRepository;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import com.knot.backend.workspace.domain.Workspace;
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

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkspaceInvitationServiceIntegrationTest {
    private final WorkspaceInvitationService workspaceInvitationService;
    private final MemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final JdbcClient jdbcClient;

    WorkspaceInvitationServiceIntegrationTest(
            WorkspaceInvitationService workspaceInvitationService,
            MemberRepository memberRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            JdbcClient jdbcClient
    ) {
        this.workspaceInvitationService = workspaceInvitationService;
        this.memberRepository = memberRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.jdbcClient = jdbcClient;
    }

    @DisplayName("동시 ensure-active 요청은 하나의 활성 초대에 수렴한다")
    @Test
    void issue_success_concurrentRequestsReturnSameInvitation() throws Exception {
        // given
        Instant now = Instant.now();
        Member member = memberRepository.save(
                Member.create(
                        uniqueNickname(),
                        null
                )
        );
        Workspace workspace = workspaceRepository.save(
                Workspace.create(
                        "동시성 팀",
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
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Callable<WorkspaceInvitationResult> issueInvitation = () -> {
            barrier.await();
            return workspaceInvitationService.issue(
                    workspace.getId(),
                    member.getId()
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
            assertThat(countInvitations(workspace.getId())).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
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
}
