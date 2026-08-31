package com.knot.backend.notion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.notion.domain.NotionConnection;
import com.knot.backend.notion.domain.NotionConnectionRepository;
import com.knot.backend.notion.domain.NotionOAuthAuthorization;
import com.knot.backend.notion.domain.NotionOAuthAuthorizationRepository;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import com.knot.backend.workspace.infrastructure.WorkspaceRepositoryAdapter;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@Import({TestcontainersConfiguration.class, WorkspaceRepositoryAdapter.class,
        NotionOAuthAuthorizationRepositoryAdapter.class, NotionConnectionRepositoryAdapter.class})
@DataJpaTest
class NotionOAuthRepositoryIntegrationTest {
    private static final URI CALLBACK_URI = URI.create("https://api.knot.test/notion/oauth/callback");
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");
    private static final Instant CREATED_AT_WITH_NANOS = Instant.parse("2026-08-31T00:00:00.123456789Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-31T00:10:00Z");

    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private NotionOAuthAuthorizationRepository authorizationRepository;
    @Autowired
    private NotionConnectionRepository connectionRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbcClient;

    @DisplayName("OAuth authorization을 저장하고 pending과 state hash lock 조회로 가져온다")
    @Test
    void saveAuthorization_success() {
        // given
        TestWorkspaceContext context = saveWorkspaceContext(
                "Knot 팀",
                1L
        );
        NotionOAuthAuthorization authorization = createAuthorization(
                context.workspaceId(),
                context.memberId(),
                "state-hash",
                CREATED_AT
        );

        // when
        NotionOAuthAuthorization savedAuthorization = saveAndReload(authorization);

        // then
        assertThat(authorizationRepository.findPendingByWorkspaceId(context.workspaceId())).get()
                .extracting(NotionOAuthAuthorization::getId)
                .isEqualTo(savedAuthorization.getId());
        assertThat(authorizationRepository.findByStateHashForUpdate("state-hash")).get()
                .extracting(NotionOAuthAuthorization::getId)
                .isEqualTo(savedAuthorization.getId());
        assertThat(savedAuthorization.getCallbackUri()).isEqualTo(CALLBACK_URI);
    }

    @DisplayName("한 워크스페이스에는 미완료 OAuth authorization을 두 개 저장할 수 없다")
    @Test
    void saveAuthorization_failure_duplicatePending() {
        // given
        TestWorkspaceContext context = saveWorkspaceContext(
                "Knot 팀",
                2L
        );
        saveAndFlush(
                createAuthorization(
                        context.workspaceId(),
                        context.memberId(),
                        "first-state-hash",
                        CREATED_AT
                )
        );
        NotionOAuthAuthorization duplicate = createAuthorization(
                context.workspaceId(),
                context.memberId(),
                "second-state-hash",
                CREATED_AT.plusSeconds(1)
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(duplicate);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("기존 OAuth authorization을 무효화하면 같은 워크스페이스에 새 pending을 저장할 수 있다")
    @Test
    void saveAuthorization_success_afterInvalidation() {
        // given
        TestWorkspaceContext context = saveWorkspaceContext(
                "Knot 팀",
                3L
        );
        NotionOAuthAuthorization previous = saveAndFlush(
                createAuthorization(
                        context.workspaceId(),
                        context.memberId(),
                        "previous-state-hash",
                        CREATED_AT
                )
        );
        previous.invalidate(CREATED_AT.plusSeconds(1));
        authorizationRepository.save(previous);
        NotionOAuthAuthorization current = createAuthorization(
                context.workspaceId(),
                context.memberId(),
                "current-state-hash",
                CREATED_AT.plusSeconds(2)
        );

        // when
        NotionOAuthAuthorization savedCurrent = saveAndReload(current);

        // then
        assertThat(authorizationRepository.findPendingByWorkspaceId(context.workspaceId())).get()
                .extracting(NotionOAuthAuthorization::getId)
                .isEqualTo(savedCurrent.getId());
        assertThat(
                authorizationRepository.existsNewerAuthorization(
                        context.workspaceId(),
                        previous.getId()
                )
        ).isTrue();
    }

    @DisplayName("이미 소비된 OAuth authorization은 pending으로 조회하지 않는다")
    @Test
    void findPendingByWorkspaceId_success_excludesConsumed() {
        // given
        TestWorkspaceContext context = saveWorkspaceContext(
                "Knot 팀",
                4L
        );
        NotionOAuthAuthorization authorization = saveAndFlush(
                createAuthorization(
                        context.workspaceId(),
                        context.memberId(),
                        "state-hash",
                        CREATED_AT
                )
        );
        authorization.consume(CREATED_AT.plusSeconds(1));
        authorizationRepository.save(authorization);
        entityManager.clear();

        // when
        boolean found = authorizationRepository.findPendingByWorkspaceId(context.workspaceId())
                .isPresent();

        // then
        assertThat(found).isFalse();
    }

    @DisplayName("같은 state hash는 서로 다른 워크스페이스에서도 중복 저장할 수 없다")
    @Test
    void saveAuthorization_failure_duplicateStateHash() {
        // given
        TestWorkspaceContext firstContext = saveWorkspaceContext(
                "첫 팀",
                5L
        );
        TestWorkspaceContext secondContext = saveWorkspaceContext(
                "둘째 팀",
                6L
        );
        saveAndFlush(
                createAuthorization(
                        firstContext.workspaceId(),
                        firstContext.memberId(),
                        "state-hash",
                        CREATED_AT
                )
        );
        NotionOAuthAuthorization duplicate = createAuthorization(
                secondContext.workspaceId(),
                secondContext.memberId(),
                "state-hash",
                CREATED_AT.plusSeconds(1)
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(duplicate);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("Connection을 저장하고 workspace ID로 조회한다")
    @Test
    void saveConnection_success() {
        // given
        TestWorkspaceContext context = saveWorkspaceContext(
                "Knot 팀",
                7L
        );
        NotionConnection connection = createConnection(
                context.workspaceId(),
                context.memberId(),
                "notion-workspace-id",
                CREATED_AT_WITH_NANOS
        );
        Instant expectedCreatedAt = CREATED_AT_WITH_NANOS.truncatedTo(ChronoUnit.MICROS);

        // when
        NotionConnection savedConnection = saveAndReload(connection);

        // then
        assertThat(connectionRepository.findByWorkspaceId(context.workspaceId())).get()
                .extracting(NotionConnection::getId)
                .isEqualTo(savedConnection.getId());
        assertThat(connectionRepository.findByWorkspaceIdForUpdate(context.workspaceId())).get()
                .extracting(
                        NotionConnection::getUpdatedAt,
                        NotionConnection::getNotionWorkspaceIcon,
                        NotionConnection::getOwnerType,
                        NotionConnection::getOwnerUserId,
                        NotionConnection::getDuplicatedTemplateId,
                        NotionConnection::getRequestId
                )
                .containsExactly(
                        expectedCreatedAt,
                        "https://static.notion.test/icon.png",
                        "user",
                        "notion-owner-user-id",
                        "template-id",
                        "request-id"
                );
    }

    @DisplayName("한 워크스페이스에는 Connection을 하나만 저장할 수 있다")
    @Test
    void saveConnection_failure_duplicateWorkspace() {
        // given
        TestWorkspaceContext context = saveWorkspaceContext(
                "Knot 팀",
                8L
        );
        saveAndFlush(
                createConnection(
                        context.workspaceId(),
                        context.memberId(),
                        "first-notion-workspace-id",
                        CREATED_AT
                )
        );
        NotionConnection duplicate = createConnection(
                context.workspaceId(),
                context.memberId(),
                "second-notion-workspace-id",
                CREATED_AT.plusSeconds(1)
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(duplicate);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("workspace owner에는 Notion user ID를 함께 저장할 수 없다")
    @Test
    void saveConnection_failure_workspaceOwnerWithUserId() {
        // given
        TestWorkspaceContext context = saveWorkspaceContext(
                "Knot 팀",
                10L
        );

        // when
        ThrowingCallable action = () -> jdbcClient.sql("""
                INSERT INTO notion_connections (
                    workspace_id,
                    access_token_ciphertext,
                    notion_workspace_id,
                    bot_id,
                    owner_type,
                    owner_user_id,
                    authorizing_member_id,
                    created_at,
                    updated_at
                ) VALUES (
                    :workspaceId,
                    'access-envelope',
                    'notion-workspace-id',
                    'bot-id',
                    'workspace',
                    'unexpected-owner-user-id',
                    :authorizingMemberId,
                    TIMESTAMPTZ '2026-08-31 00:00:00+00',
                    TIMESTAMPTZ '2026-08-31 00:00:00+00'
                )
                """)
                .param(
                        "workspaceId",
                        context.workspaceId()
                )
                .param(
                        "authorizingMemberId",
                        context.memberId()
                )
                .update();

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("Connection은 다른 Notion workspace 정보로 교체할 수 있다")
    @Test
    void replaceConnection_success_differentNotionWorkspace() {
        // given
        TestWorkspaceContext context = saveWorkspaceContext(
                "Knot 팀",
                9L
        );
        NotionConnection connection = saveAndFlush(
                createConnection(
                        context.workspaceId(),
                        context.memberId(),
                        "previous-notion-workspace-id",
                        CREATED_AT
                )
        );
        entityManager.clear();
        NotionConnection loadedConnection = connectionRepository.findByWorkspaceIdForUpdate(context.workspaceId())
                .orElseThrow();

        // when
        loadedConnection.replace(
                "new-access-envelope",
                null,
                "new-notion-workspace-id",
                null,
                null,
                "new-bot-id",
                "workspace",
                null,
                null,
                "new-request-id",
                context.memberId(),
                CREATED_AT.plusSeconds(1)
        );
        saveAndFlush(loadedConnection);

        // then
        assertThat(connectionRepository.findByWorkspaceId(context.workspaceId())).get()
                .extracting(
                        NotionConnection::getId,
                        NotionConnection::getNotionWorkspaceId,
                        NotionConnection::getRefreshTokenCiphertext,
                        NotionConnection::getNotionWorkspaceIcon,
                        NotionConnection::getOwnerType,
                        NotionConnection::getOwnerUserId,
                        NotionConnection::getDuplicatedTemplateId,
                        NotionConnection::getRequestId
                )
                .containsExactly(
                        connection.getId(),
                        "new-notion-workspace-id",
                        null,
                        null,
                        "workspace",
                        null,
                        null,
                        "new-request-id"
                );
    }

    private TestWorkspaceContext saveWorkspaceContext(
            String workspaceName,
            long memberNumber
    ) {
        long memberId = saveMember(memberNumber);
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        workspaceName,
                        CREATED_AT
                )
        );
        return new TestWorkspaceContext(
                workspace.getId(),
                memberId
        );
    }

    private long saveMember(long memberNumber) {
        return jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES (:nickname, NULL)
                RETURNING id
                """)
                .param(
                        "nickname",
                        "member" + memberNumber
                )
                .query(Long.class)
                .single();
    }

    private NotionOAuthAuthorization createAuthorization(
            Long workspaceId,
            Long authorizingMemberId,
            String stateHash,
            Instant createdAt
    ) {
        return NotionOAuthAuthorization.create(
                workspaceId,
                authorizingMemberId,
                stateHash,
                CALLBACK_URI,
                createdAt,
                createdAt.plusSeconds(600)
        );
    }

    private NotionConnection createConnection(
            Long workspaceId,
            Long authorizingMemberId,
            String notionWorkspaceId,
            Instant createdAt
    ) {
        return NotionConnection.create(
                workspaceId,
                "access-envelope",
                "refresh-envelope",
                notionWorkspaceId,
                "Knot Notion",
                "https://static.notion.test/icon.png",
                "bot-id",
                "user",
                "notion-owner-user-id",
                "template-id",
                "request-id",
                authorizingMemberId,
                createdAt
        );
    }

    private Workspace saveAndFlush(Workspace workspace) {
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        entityManager.flush();
        return savedWorkspace;
    }

    private NotionOAuthAuthorization saveAndFlush(NotionOAuthAuthorization authorization) {
        NotionOAuthAuthorization savedAuthorization = authorizationRepository.save(authorization);
        entityManager.flush();
        return savedAuthorization;
    }

    private NotionOAuthAuthorization saveAndReload(NotionOAuthAuthorization authorization) {
        NotionOAuthAuthorization savedAuthorization = saveAndFlush(authorization);
        entityManager.clear();
        return authorizationRepository.findByStateHashForUpdate(savedAuthorization.getStateHash())
                .orElseThrow();
    }

    private NotionConnection saveAndFlush(NotionConnection connection) {
        NotionConnection savedConnection = connectionRepository.save(connection);
        entityManager.flush();
        return savedConnection;
    }

    private NotionConnection saveAndReload(NotionConnection connection) {
        NotionConnection savedConnection = saveAndFlush(connection);
        entityManager.clear();
        return connectionRepository.findByWorkspaceId(savedConnection.getWorkspaceId())
                .orElseThrow();
    }

    private record TestWorkspaceContext(
            Long workspaceId,
            Long memberId
    ) {
    }
}
