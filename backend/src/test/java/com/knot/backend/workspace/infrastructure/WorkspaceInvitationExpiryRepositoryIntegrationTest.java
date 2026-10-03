package com.knot.backend.workspace.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@DataJpaTest
@Import({TestcontainersConfiguration.class, WorkspaceRepositoryAdapter.class,
        WorkspaceInvitationRepositoryAdapter.class})
class WorkspaceInvitationExpiryRepositoryIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-03T00:00:00.123456789Z");

    @Autowired
    private WorkspaceRepository workspaces;
    @Autowired
    private WorkspaceInvitationRepository invitations;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbcClient;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("독립 만료를 저장하고 재조회해도 정밀도와 legacy 무효 상태를 보존한다")
    void saveAndReload_preservesIndependentExpirations(boolean invalidated) {
        // given
        Workspace workspace = workspaces.save(
                Workspace.create(
                        "독립 만료 팀",
                        CREATED_AT
                )
        );
        entityManager.flush();
        WorkspaceInvitation invitation = WorkspaceInvitation.createWithExpirations(
                workspace.getId(),
                "link-hash",
                "code-hash",
                null,
                null,
                CREATED_AT,
                CREATED_AT.plusSeconds(3600),
                CREATED_AT.plusSeconds(7200)
        );
        if (invalidated) {
            invitation.invalidate(CREATED_AT.plusSeconds(1));
        }

        // when
        WorkspaceInvitation loaded = saveAndReload(invitation);

        // then
        assertThat(loaded.getCodeExpiresAt()).isEqualTo(
                CREATED_AT.plusSeconds(3600)
                        .truncatedTo(ChronoUnit.MICROS)
        );
        assertThat(loaded.getLinkTokenExpiresAt()).isEqualTo(
                CREATED_AT.plusSeconds(7200)
                        .truncatedTo(ChronoUnit.MICROS)
        );
        assertThat(loaded.isCodeValidAt(loaded.getCodeExpiresAt())).isFalse();
        assertThat(loaded.isLinkTokenValidAt(loaded.getCodeExpiresAt())).isEqualTo(!invalidated);
        assertThat(
                invitations.findByInviteCodeHash("code-hash")
                        .orElseThrow()
                        .getId()
        ).isEqualTo(loaded.getId());
        assertThat(loaded.getInvalidatedAt()).isEqualTo(invitation.getInvalidatedAt());
    }

    @Test
    @DisplayName("구 INSERT의 null 확장 컬럼은 JPA 조회에서 기존 만료 시각으로 해석한다")
    void find_interpretsLegacyExpiration() {
        // given
        Workspace workspace = workspaces.save(
                Workspace.create(
                        "구 버전 팀",
                        CREATED_AT
                )
        );
        entityManager.flush();
        jdbcClient.sql("""
                INSERT INTO workspace_invitations
                    (workspace_id, link_token_hash, invite_code_hash, created_at, expires_at)
                VALUES (:workspaceId, 'old-link', 'old-code', '2026-10-03T00:00Z', '2026-10-04T00:00Z')
                """)
                .param(
                        "workspaceId",
                        workspace.getId()
                )
                .update();
        entityManager.clear();

        // when
        WorkspaceInvitation loaded = invitations.findByLinkTokenHash("old-link")
                .orElseThrow();

        // then
        assertThat(loaded.getCodeExpiresAt()).isEqualTo(loaded.getExpiresAt());
        assertThat(loaded.getLinkTokenExpiresAt()).isEqualTo(loaded.getExpiresAt());
        assertThat(loaded.isCodeValidAt(loaded.getCreatedAt())).isTrue();
        assertThat(loaded.isLinkTokenValidAt(loaded.getExpiresAt())).isFalse();
        assertThat(loaded.isValidAt(loaded.getExpiresAt())).isFalse();
    }

    private WorkspaceInvitation saveAndReload(WorkspaceInvitation invitation) {
        invitations.save(invitation);
        entityManager.flush();
        entityManager.clear();
        return invitations.findByLinkTokenHash(invitation.getLinkTokenHash())
                .orElseThrow();
    }
}
