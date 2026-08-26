package com.knot.backend.workspace.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@Import({TestcontainersConfiguration.class, WorkspaceRepositoryAdapter.class,
        WorkspaceMemberRepositoryAdapter.class})
@DataJpaTest
class WorkspaceRepositoryIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-24T00:00:00Z");
    private static final Instant JOINED_AT = Instant.parse("2026-08-24T00:01:00Z");

    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbcClient;

    @DisplayName("워크스페이스와 멤버십을 저장하고 조회한다")
    @Test
    void saveAndFindWorkspaceAndMember() {
        // given
        long memberId = saveMember(1L);
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                workspace.getId(),
                memberId,
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );

        // when
        WorkspaceMember savedWorkspaceMember = saveAndReload(workspaceMember);

        // then
        assertThat(workspaceRepository.findById(workspace.getId())).get()
                .extracting(
                        Workspace::getName,
                        Workspace::getCreatedAt
                )
                .containsExactly(
                        "Knot 팀",
                        CREATED_AT
                );
        assertThat(workspaceMemberRepository.findById(savedWorkspaceMember.getId())).get()
                .extracting(
                        WorkspaceMember::getMemberId,
                        WorkspaceMember::getJoinedAt
                )
                .containsExactly(
                        memberId,
                        JOINED_AT
                );
        assertThat(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        workspace.getId(),
                        memberId
                )
        ).isTrue();
    }

    @DisplayName("같은 워크스페이스와 멤버 조합은 중복 저장할 수 없다")
    @Test
    void rejectDuplicateWorkspaceMember() {
        // given
        long memberId = saveMember(2L);
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );
        saveAndFlush(
                WorkspaceMember.create(
                        workspace.getId(),
                        memberId,
                        WorkspaceMemberRole.MEMBER,
                        JOINED_AT
                )
        );
        WorkspaceMember duplicate = WorkspaceMember.create(
                workspace.getId(),
                memberId,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(duplicate);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("같은 워크스페이스와 멤버 조합을 동시에 저장해도 하나만 성공한다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectConcurrentDuplicateWorkspaceMember() throws Exception {
        // given
        long memberId = saveMember(3L);
        Workspace workspace = workspaceRepository.save(
                Workspace.create(
                        "Knot 동시성 팀",
                        CREATED_AT
                )
        );
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Callable<Boolean> saveMembership = () -> {
            barrier.await();
            try {
                workspaceMemberRepository.save(
                        WorkspaceMember.create(
                                workspace.getId(),
                                memberId,
                                WorkspaceMemberRole.MEMBER,
                                JOINED_AT
                        )
                );
                return true;
            } catch (DataIntegrityViolationException exception) {
                return false;
            }
        };

        try {
            // when
            Future<Boolean> firstResult = executorService.submit(saveMembership);
            Future<Boolean> secondResult = executorService.submit(saveMembership);
            List<Boolean> results = List.of(
                    firstResult.get(),
                    secondResult.get()
            );

            // then
            assertThat(results).containsExactlyInAnyOrder(
                    true,
                    false
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    @DisplayName("존재하지 않는 워크스페이스를 참조하는 멤버십은 저장할 수 없다")
    @Test
    void rejectMissingWorkspaceReference() {
        // given
        long memberId = saveMember(4L);
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                Long.MAX_VALUE,
                memberId,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(workspaceMember);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("존재하지 않는 멤버를 참조하는 멤버십은 저장할 수 없다")
    @Test
    void rejectMissingMemberReference() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                workspace.getId(),
                Long.MAX_VALUE,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(workspaceMember);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    private long saveMember(long githubId) {
        return jdbcClient.sql("""
                INSERT INTO member (github_id, nickname, profile_image_url)
                VALUES (:githubId, :nickname, NULL)
                RETURNING id
                """)
                .param(
                        "githubId",
                        githubId
                )
                .param(
                        "nickname",
                        "member" + githubId
                )
                .query(Long.class)
                .single();
    }

    private Workspace saveAndFlush(Workspace workspace) {
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        entityManager.flush();
        return savedWorkspace;
    }

    private WorkspaceMember saveAndFlush(WorkspaceMember workspaceMember) {
        WorkspaceMember savedWorkspaceMember = workspaceMemberRepository.save(workspaceMember);
        entityManager.flush();
        return savedWorkspaceMember;
    }

    private WorkspaceMember saveAndReload(WorkspaceMember workspaceMember) {
        WorkspaceMember savedWorkspaceMember = saveAndFlush(workspaceMember);
        entityManager.clear();
        return workspaceMemberRepository.findById(savedWorkspaceMember.getId())
                .orElseThrow();
    }
}
