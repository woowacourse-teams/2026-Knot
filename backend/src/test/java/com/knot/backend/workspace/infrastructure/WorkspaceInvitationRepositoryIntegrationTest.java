package com.knot.backend.workspace.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceInvitation;
import com.knot.backend.workspace.domain.WorkspaceInvitationRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@Import({TestcontainersConfiguration.class, WorkspaceRepositoryAdapter.class,
        WorkspaceInvitationRepositoryAdapter.class})
@DataJpaTest
class WorkspaceInvitationRepositoryIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-29T00:00:00Z");
    private static final Instant CREATED_AT_WITH_NANOS = Instant.parse("2026-08-29T00:00:00.123456789Z");
    private static final String LINK_TOKEN_HASH = "link-token-hash";
    private static final String INVITE_CODE_HASH = "invite-code-hash";

    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private WorkspaceInvitationRepository workspaceInvitationRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @DisplayName("워크스페이스 초대를 저장하고 링크 토큰 해시와 초대 코드 해시로 조회한다")
    @Test
    void saveAndFindByHashes_success() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );
        WorkspaceInvitation invitation = createInvitation(
                workspace.getId(),
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT
        );

        // when
        WorkspaceInvitation savedInvitation = saveAndReload(invitation);

        // then
        assertThat(workspaceInvitationRepository.findByLinkTokenHash(LINK_TOKEN_HASH)).get()
                .extracting(WorkspaceInvitation::getId)
                .isEqualTo(savedInvitation.getId());
        assertThat(workspaceInvitationRepository.findByInviteCodeHash(INVITE_CODE_HASH)).get()
                .extracting(WorkspaceInvitation::getId)
                .isEqualTo(savedInvitation.getId());
        assertThat(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(workspace.getId())).get()
                .extracting(WorkspaceInvitation::getId)
                .isEqualTo(savedInvitation.getId());
        assertThat(savedInvitation.hasRecoverableSecrets()).isFalse();
    }

    @DisplayName("V4 암호문 envelope를 저장하고 다시 조회한다")
    @Test
    void saveAndFind_success_secretEnvelopes() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                workspace.getId(),
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                "v1:link-nonce:link-ciphertext",
                "v1:code-nonce:code-ciphertext",
                CREATED_AT
        );

        // when
        WorkspaceInvitation savedInvitation = saveAndReload(invitation);

        // then
        assertThat(savedInvitation.getLinkTokenCiphertext()).isEqualTo("v1:link-nonce:link-ciphertext");
        assertThat(savedInvitation.getInviteCodeCiphertext()).isEqualTo("v1:code-nonce:code-ciphertext");
        assertThat(savedInvitation.hasRecoverableSecrets()).isTrue();
    }

    @DisplayName("V4는 링크 토큰과 초대 코드 암호문 중 하나만 저장하는 Row를 거부한다")
    @Test
    void save_failure_incompleteSecretEnvelopes() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );

        // when
        ThrowingCallable action = () -> insertInvitationWithEnvelopes(
                workspace.getId(),
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                "v1:link-nonce:link-ciphertext",
                null,
                CREATED_AT.plus(WorkspaceInvitation.VALIDITY_PERIOD),
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("같은 링크 토큰 해시는 서로 다른 워크스페이스에서도 중복 저장할 수 없다")
    @Test
    void save_failure_duplicateLinkTokenHash() {
        // given
        Workspace firstWorkspace = saveAndFlush(
                Workspace.create(
                        "첫 팀",
                        CREATED_AT
                )
        );
        Workspace secondWorkspace = saveAndFlush(
                Workspace.create(
                        "둘째 팀",
                        CREATED_AT
                )
        );
        saveAndFlush(
                createInvitation(
                        firstWorkspace.getId(),
                        LINK_TOKEN_HASH,
                        "first-code-hash",
                        CREATED_AT
                )
        );
        WorkspaceInvitation duplicate = createInvitation(
                secondWorkspace.getId(),
                LINK_TOKEN_HASH,
                "second-code-hash",
                CREATED_AT
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(duplicate);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("같은 초대 코드 해시는 서로 다른 워크스페이스에서도 중복 저장할 수 없다")
    @Test
    void save_failure_duplicateInviteCodeHash() {
        // given
        Workspace firstWorkspace = saveAndFlush(
                Workspace.create(
                        "첫 팀",
                        CREATED_AT
                )
        );
        Workspace secondWorkspace = saveAndFlush(
                Workspace.create(
                        "둘째 팀",
                        CREATED_AT
                )
        );
        saveAndFlush(
                createInvitation(
                        firstWorkspace.getId(),
                        "first-link-hash",
                        INVITE_CODE_HASH,
                        CREATED_AT
                )
        );
        WorkspaceInvitation duplicate = createInvitation(
                secondWorkspace.getId(),
                "second-link-hash",
                INVITE_CODE_HASH,
                CREATED_AT
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(duplicate);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("한 워크스페이스에는 미무효화 초대를 두 개 저장할 수 없다")
    @Test
    void save_failure_duplicateUninvalidatedInvitation() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );
        saveAndFlush(
                createInvitation(
                        workspace.getId(),
                        "first-link-hash",
                        "first-code-hash",
                        CREATED_AT
                )
        );
        WorkspaceInvitation duplicate = createInvitation(
                workspace.getId(),
                "second-link-hash",
                "second-code-hash",
                CREATED_AT.plusSeconds(1)
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(duplicate);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("만료됐더라도 무효화되지 않은 초대가 있으면 새 초대를 저장할 수 없다")
    @Test
    void save_failure_expiredButUninvalidatedInvitation() {
        // given
        Instant previousCreatedAt = CREATED_AT.minus(WorkspaceInvitation.VALIDITY_PERIOD)
                .minusSeconds(1);
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        previousCreatedAt
                )
        );
        saveAndFlush(
                createInvitation(
                        workspace.getId(),
                        "expired-link-hash",
                        "expired-code-hash",
                        previousCreatedAt
                )
        );
        WorkspaceInvitation newInvitation = createInvitation(
                workspace.getId(),
                "new-link-hash",
                "new-code-hash",
                CREATED_AT
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(newInvitation);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("존재하지 않는 워크스페이스를 참조하는 초대는 저장할 수 없다")
    @Test
    void save_failure_missingWorkspaceReference() {
        // given
        WorkspaceInvitation invitation = createInvitation(
                Long.MAX_VALUE,
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT
        );

        // when
        ThrowingCallable action = () -> saveAndFlush(invitation);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("만료 시각이 생성 시각으로부터 24시간보다 짧으면 초대를 저장할 수 없다")
    @Test
    void save_failure_expirationLessThanTwentyFourHours() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );

        // when
        ThrowingCallable action = () -> insertInvitation(
                workspace.getId(),
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT.plus(WorkspaceInvitation.VALIDITY_PERIOD)
                        .minusSeconds(1),
                null,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("만료 시각이 생성 시각으로부터 24시간보다 길면 초대를 저장할 수 없다")
    @Test
    void save_failure_expirationGreaterThanTwentyFourHours() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );

        // when
        ThrowingCallable action = () -> insertInvitation(
                workspace.getId(),
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT.plus(WorkspaceInvitation.VALIDITY_PERIOD)
                        .plusSeconds(1),
                null,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("무효화 시각이 생성 시각보다 이르면 초대를 저장할 수 없다")
    @Test
    void save_failure_invalidInvalidation() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );

        // when
        ThrowingCallable action = () -> insertInvitation(
                workspace.getId(),
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT.plus(WorkspaceInvitation.VALIDITY_PERIOD),
                CREATED_AT.minusSeconds(1),
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("기존 초대를 무효화하면 새 초대를 저장하고 기존 이력을 조회할 수 있다")
    @Test
    void save_success_afterInvalidation() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );
        WorkspaceInvitation previousInvitation = saveAndFlush(
                createInvitation(
                        workspace.getId(),
                        "previous-link-hash",
                        "previous-code-hash",
                        CREATED_AT
                )
        );
        Instant invalidatedAt = CREATED_AT.plusSeconds(1);
        previousInvitation.invalidate(invalidatedAt);
        workspaceInvitationRepository.save(previousInvitation);
        WorkspaceInvitation newInvitation = createInvitation(
                workspace.getId(),
                "new-link-hash",
                "new-code-hash",
                CREATED_AT.plusSeconds(2)
        );

        // when
        WorkspaceInvitation savedNewInvitation = workspaceInvitationRepository.save(newInvitation);
        entityManager.clear();

        // then
        assertThat(workspaceInvitationRepository.findByLinkTokenHash("previous-link-hash")).get()
                .extracting(WorkspaceInvitation::getInvalidatedAt)
                .isEqualTo(invalidatedAt);
        assertThat(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(workspace.getId())).get()
                .extracting(WorkspaceInvitation::getId)
                .isEqualTo(savedNewInvitation.getId());
        assertThat(countInvitations(workspace.getId())).isEqualTo(2);
    }

    @DisplayName("나노초 생성 시각으로 저장해도 재조회한 만료 시각과 생성 시각이 일치한다")
    @Test
    void saveAndFind_success_preservesMicrosecondPrecision() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT_WITH_NANOS
                )
        );
        WorkspaceInvitation invitation = createInvitation(
                workspace.getId(),
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT_WITH_NANOS
        );

        // when
        WorkspaceInvitation savedInvitation = saveAndReload(invitation);

        // then
        assertThat(savedInvitation.getCreatedAt()).isEqualTo(invitation.getCreatedAt());
        assertThat(savedInvitation.getExpiresAt()).isEqualTo(invitation.getExpiresAt());
    }

    @DisplayName("나노초 무효화 시각으로 저장해도 재조회한 무효화 시각이 일치한다")
    @Test
    void saveAndFind_success_preservesInvalidatedAtMicrosecondPrecision() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );
        WorkspaceInvitation invitation = createInvitation(
                workspace.getId(),
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT
        );
        Instant invalidatedAt = CREATED_AT_WITH_NANOS.plusSeconds(1);
        Instant expectedInvalidatedAt = invalidatedAt.truncatedTo(ChronoUnit.MICROS);
        invitation.invalidate(invalidatedAt);

        // when
        WorkspaceInvitation savedInvitation = saveAndReload(invitation);

        // then
        assertThat(savedInvitation.getInvalidatedAt()).isEqualTo(expectedInvalidatedAt);
        assertThat(savedInvitation.getInvalidatedAt()).isEqualTo(invitation.getInvalidatedAt());
    }

    @DisplayName("이미 무효화된 초대의 stale entity 저장은 낙관적 잠금 실패로 거부한다")
    @Test
    void save_failure_staleEntityRestoresInvalidation() {
        // given
        Workspace workspace = saveAndFlush(
                Workspace.create(
                        "Knot 팀",
                        CREATED_AT
                )
        );
        WorkspaceInvitation savedInvitation = saveAndFlush(
                createInvitation(
                        workspace.getId(),
                        LINK_TOKEN_HASH,
                        INVITE_CODE_HASH,
                        CREATED_AT
                )
        );
        entityManager.detach(savedInvitation);
        WorkspaceInvitation loadedInvitation = workspaceInvitationRepository.findByLinkTokenHash(LINK_TOKEN_HASH)
                .orElseThrow();
        loadedInvitation.invalidate(CREATED_AT.plusSeconds(1));
        saveAndFlush(loadedInvitation);
        entityManager.clear();

        // when
        ThrowingCallable action = () -> saveAndFlush(savedInvitation);

        // then
        assertThatThrownBy(action).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(workspaceInvitationRepository.findByLinkTokenHash(LINK_TOKEN_HASH)).get()
                .extracting(WorkspaceInvitation::getInvalidatedAt)
                .isEqualTo(CREATED_AT.plusSeconds(1));
    }

    @DisplayName("같은 워크스페이스에 미무효화 초대를 동시에 저장해도 하나만 성공한다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_failure_concurrentUninvalidatedInvitation() throws Exception {
        // given
        Workspace workspace = workspaceRepository.save(
                Workspace.create(
                        "동시성 팀",
                        CREATED_AT
                )
        );
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Callable<Boolean> saveInvitation = () -> {
            long threadId = Thread.currentThread()
                    .threadId();
            barrier.await();
            try {
                workspaceInvitationRepository.save(
                        createInvitation(
                                workspace.getId(),
                                "concurrent-link-hash-" + threadId,
                                "concurrent-code-hash-" + threadId,
                                CREATED_AT
                        )
                );
                return true;
            } catch (DataIntegrityViolationException exception) {
                return false;
            }
        };

        try {
            // when
            Future<Boolean> firstResult = executorService.submit(saveInvitation);
            Future<Boolean> secondResult = executorService.submit(saveInvitation);
            List<Boolean> results = List.of(
                    firstResult.get(),
                    secondResult.get()
            );

            // then
            assertThat(results).containsExactlyInAnyOrder(
                    true,
                    false
            );
            assertThat(countInvitations(workspace.getId())).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @DisplayName("새 초대 저장 실패 시 기존 초대 무효화를 rollback한다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reissue_failure_rollsBackInvalidation() {
        // given
        Long targetWorkspaceId = insertWorkspace("대상 팀");
        Long otherWorkspaceId = insertWorkspace("다른 팀");
        insertInvitation(
                targetWorkspaceId,
                "target-link-hash",
                "target-code-hash",
                CREATED_AT.plus(WorkspaceInvitation.VALIDITY_PERIOD),
                null,
                CREATED_AT
        );
        insertInvitation(
                otherWorkspaceId,
                "duplicate-link-hash",
                "other-code-hash",
                CREATED_AT.plus(WorkspaceInvitation.VALIDITY_PERIOD),
                null,
                CREATED_AT
        );
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // when
        ThrowingCallable action = () -> transactionTemplate.executeWithoutResult(status -> {
            WorkspaceInvitation previousInvitation = workspaceInvitationRepository
                    .findUninvalidatedByWorkspaceId(targetWorkspaceId)
                    .orElseThrow();
            previousInvitation.invalidate(CREATED_AT.plusSeconds(1));
            workspaceInvitationRepository.save(previousInvitation);
            workspaceInvitationRepository.save(
                    WorkspaceInvitation.create(
                            targetWorkspaceId,
                            "duplicate-link-hash",
                            "new-code-hash",
                            CREATED_AT.plusSeconds(2)
                    )
            );
        });

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(workspaceInvitationRepository.findUninvalidatedByWorkspaceId(targetWorkspaceId)).get()
                .extracting(WorkspaceInvitation::getInvalidatedAt)
                .isNull();
    }

    private WorkspaceInvitation createInvitation(
            Long workspaceId,
            String linkTokenHash,
            String inviteCodeHash,
            Instant createdAt
    ) {
        return WorkspaceInvitation.create(
                workspaceId,
                linkTokenHash,
                inviteCodeHash,
                createdAt
        );
    }

    private Workspace saveAndFlush(Workspace workspace) {
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        entityManager.flush();
        return savedWorkspace;
    }

    private WorkspaceInvitation saveAndFlush(WorkspaceInvitation invitation) {
        WorkspaceInvitation savedInvitation = workspaceInvitationRepository.save(invitation);
        entityManager.flush();
        return savedInvitation;
    }

    private WorkspaceInvitation saveAndReload(WorkspaceInvitation invitation) {
        WorkspaceInvitation savedInvitation = saveAndFlush(invitation);
        entityManager.clear();
        return workspaceInvitationRepository.findByLinkTokenHash(savedInvitation.getLinkTokenHash())
                .orElseThrow();
    }

    private void insertInvitation(
            Long workspaceId,
            String linkTokenHash,
            String inviteCodeHash,
            Instant expiresAt,
            Instant invalidatedAt,
            Instant createdAt
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_invitations (
                    workspace_id,
                    link_token_hash,
                    invite_code_hash,
                    expires_at,
                    invalidated_at,
                    created_at,
                    version
                ) VALUES (
                    :workspaceId,
                    :linkTokenHash,
                    :inviteCodeHash,
                    :expiresAt,
                    :invalidatedAt,
                    :createdAt,
                    0
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
                        toOffsetDateTime(expiresAt)
                )
                .param(
                        "invalidatedAt",
                        toOffsetDateTime(invalidatedAt)
                )
                .param(
                        "createdAt",
                        toOffsetDateTime(createdAt)
                )
                .update();
    }

    private void insertInvitationWithEnvelopes(
            Long workspaceId,
            String linkTokenHash,
            String inviteCodeHash,
            String linkTokenCiphertext,
            String inviteCodeCiphertext,
            Instant expiresAt,
            Instant createdAt
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_invitations (
                    workspace_id,
                    link_token_hash,
                    invite_code_hash,
                    link_token_ciphertext,
                    invite_code_ciphertext,
                    expires_at,
                    created_at
                ) VALUES (
                    :workspaceId,
                    :linkTokenHash,
                    :inviteCodeHash,
                    :linkTokenCiphertext,
                    :inviteCodeCiphertext,
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
                        "linkTokenCiphertext",
                        linkTokenCiphertext
                )
                .param(
                        "inviteCodeCiphertext",
                        inviteCodeCiphertext
                )
                .param(
                        "expiresAt",
                        toOffsetDateTime(expiresAt)
                )
                .param(
                        "createdAt",
                        toOffsetDateTime(createdAt)
                )
                .update();
    }

    private Long insertWorkspace(String name) {
        return jdbcClient.sql("""
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
                        toOffsetDateTime(CREATED_AT)
                )
                .query(Long.class)
                .single();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atOffset(ZoneOffset.UTC);
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
