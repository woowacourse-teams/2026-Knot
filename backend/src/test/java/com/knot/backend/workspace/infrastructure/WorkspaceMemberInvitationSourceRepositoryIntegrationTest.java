package com.knot.backend.workspace.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@Import({TestcontainersConfiguration.class, WorkspaceMemberRepositoryAdapter.class})
@DataJpaTest
class WorkspaceMemberInvitationSourceRepositoryIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-03T00:00:00Z");
    private static final Instant JOINED_AT = CREATED_AT.plusSeconds(60);

    @Autowired
    private WorkspaceMemberRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("여러 멤버가 같은 초대를 출처로 저장하고 재조회한다")
    void save_roundTripsSharedInvitationSource() {
        // given
        Workspace workspace = workspace();
        WorkspaceInvitation invitation = invitation(
                workspace,
                "shared"
        );
        WorkspaceMember first = saveAndFlush(
                WorkspaceMember.createFromInvitation(
                        workspace.getId(),
                        member("첫멤버"),
                        invitation.getId(),
                        JOINED_AT
                )
        );
        WorkspaceMember second = WorkspaceMember.createFromInvitation(
                workspace.getId(),
                member("둘째멤버"),
                invitation.getId(),
                JOINED_AT.plusSeconds(1)
        );

        // when
        WorkspaceMember saved = saveAndFlush(second);

        // then
        entityManager.clear();
        WorkspaceMember reloaded = repository.findById(saved.getId())
                .orElseThrow();
        assertThat(reloaded.getSourceInvitationId()).isEqualTo(invitation.getId());
        assertThat(reloaded.getJoinedAt()).isEqualTo(JOINED_AT.plusSeconds(1));
        assertThat(reloaded.getRole()).isEqualTo(WorkspaceMemberRole.MEMBER);
        assertThat(reloaded.isLastViewed()).isFalse();
        assertThat(
                repository.findById(first.getId())
                        .orElseThrow()
                        .getSourceInvitationId()
        ).isEqualTo(invitation.getId());
    }

    @Test
    @DisplayName("생성자 자동 참여는 초대 출처 없이 저장한다")
    void save_roundTripsOwnerWithoutSource() {
        // given
        Workspace workspace = workspace();
        WorkspaceMember owner = WorkspaceMember.create(
                workspace.getId(),
                member("생성자"),
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );

        // when
        WorkspaceMember saved = saveAndFlush(owner);

        // then
        entityManager.clear();
        WorkspaceMember reloaded = repository.findById(saved.getId())
                .orElseThrow();
        assertThat(reloaded.getSourceInvitationId()).isNull();
        assertThat(reloaded.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(reloaded.getJoinedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    @DisplayName("탈퇴 후 다른 초대로 재가입하면 과거와 새 참여의 출처를 각각 보존한다")
    void save_preservesSourcesAcrossRejoin() {
        // given
        Workspace workspace = workspace();
        long memberId = member("재가입자");
        WorkspaceInvitation firstInvitation = invitation(
                workspace,
                "first"
        );
        WorkspaceInvitation secondInvitation = invitation(
                workspace,
                "second"
        );
        WorkspaceMember former = WorkspaceMember.createFromInvitation(
                workspace.getId(),
                memberId,
                firstInvitation.getId(),
                JOINED_AT
        );
        saveAndFlush(former);
        former.receiveOwnership();
        former.leave(
                JOINED_AT.plusSeconds(1),
                1
        );
        entityManager.flush();
        WorkspaceMember rejoining = WorkspaceMember.createFromInvitation(
                workspace.getId(),
                memberId,
                secondInvitation.getId(),
                JOINED_AT.plusSeconds(2)
        );

        // when
        WorkspaceMember saved = saveAndFlush(rejoining);

        // then
        entityManager.clear();
        WorkspaceMember oldMembership = repository.findById(former.getId())
                .orElseThrow();
        WorkspaceMember newMembership = repository.findById(saved.getId())
                .orElseThrow();
        assertThat(newMembership.getId()).isNotEqualTo(oldMembership.getId());
        assertThat(oldMembership.getSourceInvitationId()).isEqualTo(firstInvitation.getId());
        assertThat(oldMembership.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(oldMembership.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(oldMembership.getLeftAt()).isEqualTo(JOINED_AT.plusSeconds(1));
        assertThat(newMembership.getSourceInvitationId()).isEqualTo(secondInvitation.getId());
        assertThat(newMembership.getRole()).isEqualTo(WorkspaceMemberRole.MEMBER);
        assertThat(newMembership.isActive()).isTrue();
        assertThat(newMembership.isLastViewed()).isFalse();
    }

    private Workspace workspace() {
        Workspace workspace = Workspace.create(
                "출처 팀",
                CREATED_AT
        );
        entityManager.persist(workspace);
        return workspace;
    }

    private WorkspaceInvitation invitation(
            Workspace workspace,
            String suffix
    ) {
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                workspace.getId(),
                "link-" + suffix,
                "code-" + suffix,
                CREATED_AT
        );
        entityManager.persist(invitation);
        return invitation;
    }

    private long member(String nickname) {
        return jdbcClient.sql("INSERT INTO members (nickname) VALUES (:nickname) RETURNING id")
                .param(
                        "nickname",
                        nickname
                )
                .query(Long.class)
                .single();
    }

    private WorkspaceMember saveAndFlush(WorkspaceMember member) {
        WorkspaceMember saved = repository.save(member);
        entityManager.flush();
        return saved;
    }
}
