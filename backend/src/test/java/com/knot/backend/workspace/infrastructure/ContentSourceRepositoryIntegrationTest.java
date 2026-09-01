package com.knot.backend.workspace.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.workspace.domain.ContentSourceAuthorization;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationOwnerType;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationRepository;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceRepository;
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
        ContentSourceAuthorizationRepositoryAdapter.class, ContentSourceConnectionRepositoryAdapter.class})
@DataJpaTest
class ContentSourceRepositoryIntegrationTest {
    private static final URI CALLBACK_URI = URI.create("https://api.knot.test/api/v1/notion/oauth/callback");
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");
    private static final Instant CREATED_AT_WITH_NANOS = Instant.parse("2026-08-31T00:00:00.123456789Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-31T00:10:00Z");

    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private ContentSourceAuthorizationRepository authorizationRepository;
    @Autowired
    private ContentSourceConnectionRepository connectionRepository;
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
        ContentSourceAuthorization authorization = createAuthorization(
                context.workspaceId(),
                context.memberId(),
                "state-hash",
                CREATED_AT
        );

        // when
        ContentSourceAuthorization savedAuthorization = saveAndReload(authorization);

        // then
        assertThat(
                authorizationRepository.findPendingByWorkspaceIdAndProvider(
                        context.workspaceId(),
                        ContentSourceProvider.NOTION
                )
        ).get()
                .extracting(ContentSourceAuthorization::getId)
                .isEqualTo(savedAuthorization.getId());
        assertThat(
                authorizationRepository.findByProviderAndStateHashForUpdate(
                        ContentSourceProvider.NOTION,
                        "state-hash"
                )
        ).get()
                .extracting(ContentSourceAuthorization::getId)
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
        ContentSourceAuthorization duplicate = createAuthorization(
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
        ContentSourceAuthorization previous = saveAndFlush(
                createAuthorization(
                        context.workspaceId(),
                        context.memberId(),
                        "previous-state-hash",
                        CREATED_AT
                )
        );
        previous.invalidate(CREATED_AT.plusSeconds(1));
        authorizationRepository.save(previous);
        ContentSourceAuthorization current = createAuthorization(
                context.workspaceId(),
                context.memberId(),
                "current-state-hash",
                CREATED_AT.plusSeconds(2)
        );

        // when
        ContentSourceAuthorization savedCurrent = saveAndReload(current);

        // then
        assertThat(
                authorizationRepository.findPendingByWorkspaceIdAndProvider(
                        context.workspaceId(),
                        ContentSourceProvider.NOTION
                )
        ).get()
                .extracting(ContentSourceAuthorization::getId)
                .isEqualTo(savedCurrent.getId());
        assertThat(
                authorizationRepository.existsNewerAuthorization(
                        context.workspaceId(),
                        ContentSourceProvider.NOTION,
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
        ContentSourceAuthorization authorization = saveAndFlush(
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
        boolean found = authorizationRepository.findPendingByWorkspaceIdAndProvider(
                context.workspaceId(),
                ContentSourceProvider.NOTION
        )
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
        ContentSourceAuthorization duplicate = createAuthorization(
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

    @DisplayName("OAuth authorization은 다른 워크스페이스의 멤버를 승인자로 저장할 수 없다")
    @Test
    void saveAuthorization_failure_crossWorkspaceAuthorizingMember() {
        // given
        TestWorkspaceContext firstContext = saveWorkspaceContext(
                "첫 팀",
                7L
        );
        TestWorkspaceContext secondContext = saveWorkspaceContext(
                "둘째 팀",
                8L
        );
        ContentSourceAuthorization authorization = createAuthorization(
                firstContext.workspaceId(),
                secondContext.memberId(),
                "cross-workspace-state-hash",
                CREATED_AT
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(authorization);

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
        ContentSourceConnection connection = createConnection(
                context.workspaceId(),
                context.memberId(),
                "notion-workspace-id",
                CREATED_AT_WITH_NANOS
        );
        Instant expectedCreatedAt = CREATED_AT_WITH_NANOS.truncatedTo(ChronoUnit.MICROS);

        // when
        ContentSourceConnection savedConnection = saveAndReload(connection);

        // then
        assertThat(
                connectionRepository.findByWorkspaceIdAndProvider(
                        context.workspaceId(),
                        ContentSourceProvider.NOTION
                )
        ).get()
                .extracting(ContentSourceConnection::getId)
                .isEqualTo(savedConnection.getId());
        assertThat(
                connectionRepository.findByWorkspaceIdAndProviderForUpdate(
                        context.workspaceId(),
                        ContentSourceProvider.NOTION
                )
        ).get()
                .extracting(
                        ContentSourceConnection::getUpdatedAt,
                        ContentSourceConnection::getExternalSourceIcon,
                        ContentSourceConnection::getAuthorizationOwnerType,
                        ContentSourceConnection::getAuthorizationOwnerId,
                        ContentSourceConnection::getExternalTemplateId,
                        ContentSourceConnection::getProviderRequestId
                )
                .containsExactly(
                        expectedCreatedAt,
                        "https://static.notion.test/icon.png",
                        ContentSourceAuthorizationOwnerType.USER,
                        "notion-owner-user-id",
                        "template-id",
                        "request-id"
                );
        assertThat(
                connectionRepository.findByIdAndWorkspaceId(
                        savedConnection.getId(),
                        context.workspaceId()
                )
        ).get()
                .extracting(ContentSourceConnection::getId)
                .isEqualTo(savedConnection.getId());
        assertThat(
                connectionRepository.findByIdAndWorkspaceId(
                        savedConnection.getId(),
                        Long.MAX_VALUE
                )
        ).isEmpty();
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
        ContentSourceConnection duplicate = createConnection(
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
                INSERT INTO content_source_connections (
                    workspace_id,
                    provider,
                    access_credential_ciphertext,
                    external_source_id,
                    provider_connection_id,
                    authorization_owner_type,
                    authorization_owner_id,
                    authorizing_member_id,
                    created_at,
                    updated_at
                ) VALUES (
                    :workspaceId,
                    'NOTION',
                    'access-envelope',
                    'notion-workspace-id',
                    'bot-id',
                    'WORKSPACE',
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

    @DisplayName("Connection은 다른 워크스페이스의 멤버를 승인자로 저장할 수 없다")
    @Test
    void saveConnection_failure_crossWorkspaceAuthorizingMember() {
        // given
        TestWorkspaceContext firstContext = saveWorkspaceContext(
                "첫 팀",
                13L
        );
        TestWorkspaceContext secondContext = saveWorkspaceContext(
                "둘째 팀",
                14L
        );
        ContentSourceConnection connection = createConnection(
                firstContext.workspaceId(),
                secondContext.memberId(),
                "cross-workspace-notion-id",
                CREATED_AT
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(connection);

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
        ContentSourceConnection connection = saveAndFlush(
                createConnection(
                        context.workspaceId(),
                        context.memberId(),
                        "previous-notion-workspace-id",
                        CREATED_AT
                )
        );
        entityManager.clear();
        ContentSourceConnection loadedConnection = connectionRepository
                .findByWorkspaceIdAndProviderForUpdate(
                        context.workspaceId(),
                        ContentSourceProvider.NOTION
                )
                .orElseThrow();

        // when
        loadedConnection.replace(
                ContentSourceProvider.NOTION,
                "new-access-envelope",
                null,
                "new-notion-workspace-id",
                null,
                null,
                "new-bot-id",
                ContentSourceAuthorizationOwnerType.WORKSPACE,
                null,
                null,
                "new-request-id",
                context.memberId(),
                CREATED_AT.plusSeconds(1)
        );
        saveAndFlush(loadedConnection);

        // then
        assertThat(
                connectionRepository.findByWorkspaceIdAndProvider(
                        context.workspaceId(),
                        ContentSourceProvider.NOTION
                )
        ).get()
                .extracting(
                        ContentSourceConnection::getId,
                        ContentSourceConnection::getExternalSourceId,
                        ContentSourceConnection::getRefreshCredentialCiphertext,
                        ContentSourceConnection::getExternalSourceIcon,
                        ContentSourceConnection::getAuthorizationOwnerType,
                        ContentSourceConnection::getAuthorizationOwnerId,
                        ContentSourceConnection::getExternalTemplateId,
                        ContentSourceConnection::getProviderRequestId
                )
                .containsExactly(
                        connection.getId(),
                        "new-notion-workspace-id",
                        null,
                        null,
                        ContentSourceAuthorizationOwnerType.WORKSPACE,
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
        saveWorkspaceMember(
                workspace.getId(),
                memberId
        );
        return new TestWorkspaceContext(
                workspace.getId(),
                memberId
        );
    }

    private void saveWorkspaceMember(
            Long workspaceId,
            Long memberId
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, 'OWNER', TIMESTAMPTZ '2026-08-31 00:00:00+00')
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

    private ContentSourceAuthorization createAuthorization(
            Long workspaceId,
            Long authorizingMemberId,
            String stateHash,
            Instant createdAt
    ) {
        return ContentSourceAuthorization.create(
                workspaceId,
                ContentSourceProvider.NOTION,
                authorizingMemberId,
                stateHash,
                CALLBACK_URI,
                createdAt,
                createdAt.plusSeconds(600)
        );
    }

    private ContentSourceConnection createConnection(
            Long workspaceId,
            Long authorizingMemberId,
            String notionWorkspaceId,
            Instant createdAt
    ) {
        return ContentSourceConnection.create(
                workspaceId,
                ContentSourceProvider.NOTION,
                "access-envelope",
                "refresh-envelope",
                notionWorkspaceId,
                "Knot Notion",
                "https://static.notion.test/icon.png",
                "bot-id",
                ContentSourceAuthorizationOwnerType.USER,
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

    private ContentSourceAuthorization saveAndFlush(ContentSourceAuthorization authorization) {
        ContentSourceAuthorization savedAuthorization = authorizationRepository.save(authorization);
        entityManager.flush();
        return savedAuthorization;
    }

    private ContentSourceAuthorization saveAndReload(ContentSourceAuthorization authorization) {
        ContentSourceAuthorization savedAuthorization = saveAndFlush(authorization);
        entityManager.clear();
        return authorizationRepository.findByProviderAndStateHashForUpdate(
                ContentSourceProvider.NOTION,
                savedAuthorization.getStateHash()
        )
                .orElseThrow();
    }

    private ContentSourceConnection saveAndFlush(ContentSourceConnection connection) {
        ContentSourceConnection savedConnection = connectionRepository.save(connection);
        entityManager.flush();
        return savedConnection;
    }

    private ContentSourceConnection saveAndReload(ContentSourceConnection connection) {
        ContentSourceConnection savedConnection = saveAndFlush(connection);
        entityManager.clear();
        return connectionRepository.findByWorkspaceIdAndProvider(
                savedConnection.getWorkspaceId(),
                ContentSourceProvider.NOTION
        )
                .orElseThrow();
    }

    private record TestWorkspaceContext(
            Long workspaceId,
            Long memberId
    ) {
    }
}
