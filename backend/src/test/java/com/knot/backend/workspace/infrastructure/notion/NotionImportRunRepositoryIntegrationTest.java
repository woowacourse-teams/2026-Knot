package com.knot.backend.workspace.infrastructure.notion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import com.knot.backend.workspace.domain.NotionImportStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@Import({TestcontainersConfiguration.class, NotionImportRunRepositoryAdapter.class})
@DataJpaTest
class NotionImportRunRepositoryIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @Autowired
    private NotionImportRunRepository importRunRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbcClient;

    @DisplayName("현재 Workspace의 OWNER와 MEMBER만 같은 Import Run을 조회한다")
    @Test
    void findVisibleByIdAndMemberId_success_currentMembers() {
        // given
        long ownerId = saveMember("owner");
        long memberId = saveMember("member");
        long outsiderId = saveMember("outsider");
        long workspaceId = saveWorkspace("Knot 팀");
        long otherWorkspaceId = saveWorkspace("다른 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );
        saveWorkspaceMember(
                otherWorkspaceId,
                outsiderId,
                "OWNER"
        );
        long connectionId = saveConnection(
                workspaceId,
                ownerId
        );
        NotionImportRun savedImportRun = saveAndReload(
                NotionImportRun.create(
                        workspaceId,
                        connectionId,
                        ownerId,
                        NotionImportStatus.COMPLETED,
                        10,
                        10,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2),
                        CREATED_AT
                ),
                ownerId
        );

        // when
        Optional<NotionImportRun> ownerResult = importRunRepository.findVisibleByIdAndMemberId(
                savedImportRun.getId(),
                ownerId
        );
        Optional<NotionImportRun> memberResult = importRunRepository.findVisibleByIdAndMemberId(
                savedImportRun.getId(),
                memberId
        );
        Optional<NotionImportRun> outsiderResult = importRunRepository.findVisibleByIdAndMemberId(
                savedImportRun.getId(),
                outsiderId
        );
        Optional<NotionImportRun> missingResult = importRunRepository.findVisibleByIdAndMemberId(
                Long.MAX_VALUE,
                ownerId
        );

        // then
        assertThat(ownerResult).isPresent();
        assertThat(memberResult).isPresent();
        assertThat(outsiderResult).isEmpty();
        assertThat(missingResult).isEmpty();
    }

    @DisplayName("Import Run과 콘텐츠 소스 연결의 Workspace가 다르면 저장할 수 없다")
    @Test
    void save_failure_connectionTenantMismatch() {
        // given
        long ownerId = saveMember("owner");
        long workspaceId = saveWorkspace("Knot 팀");
        long otherWorkspaceId = saveWorkspace("다른 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        saveWorkspaceMember(
                otherWorkspaceId,
                ownerId,
                "OWNER"
        );
        long connectionId = saveConnection(
                workspaceId,
                ownerId
        );
        NotionImportRun importRun = NotionImportRun.create(
                otherWorkspaceId,
                connectionId,
                ownerId,
                NotionImportStatus.PENDING,
                null,
                0,
                null,
                null,
                CREATED_AT
        );

        // when
        ThrowingCallable action = () -> {
            importRunRepository.save(importRun);
            entityManager.flush();
        };

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("같은 콘텐츠 소스 연결에는 대기 또는 실행 중인 Import Run을 하나만 저장한다")
    @Test
    void save_failure_duplicateActiveRun() {
        // given
        TestContext context = saveContext("owner");
        importRunRepository.save(
                activeImportRun(
                        context,
                        NotionImportStatus.PENDING
                )
        );
        entityManager.flush();
        NotionImportRun duplicate = activeImportRun(
                context,
                NotionImportStatus.RUNNING
        );

        // when
        ThrowingCallable action = () -> {
            importRunRepository.save(duplicate);
            entityManager.flush();
        };

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("Import Run 테이블은 외부 실패 원문을 저장할 컬럼을 제공하지 않는다")
    @Test
    void schema_success_noFailureReasonColumn() {
        // given
        String failureReasonColumnName = "failure_reason";

        // when
        Boolean failureReasonColumnExists = jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                        AND table_name = 'notion_import_runs'
                        AND column_name = :columnName
                )
                """)
                .param(
                        "columnName",
                        failureReasonColumnName
                )
                .query(Boolean.class)
                .single();

        // then
        assertThat(failureReasonColumnExists).isFalse();
    }

    @DisplayName("Import Run의 상태, Page 수와 시작·완료 시각 DB 제약을 지킨다")
    @MethodSource("invalidConstraintCases")
    @ParameterizedTest(name = "{0}")
    void save_failure_databaseConstraints(
            String caseName,
            String status,
            Integer totalPageCount,
            int processedPageCount,
            String startedAt,
            String completedAt
    ) {
        // given
        TestContext context = saveContext(caseName);

        // when
        ThrowingCallable action = () -> jdbcClient.sql("""
                INSERT INTO notion_import_runs (
                    workspace_id,
                    content_source_connection_id,
                    requested_by_member_id,
                    status,
                    total_page_count,
                    processed_page_count,
                    started_at,
                    completed_at,
                    last_heartbeat_at,
                    created_at
                ) VALUES (
                    :workspaceId,
                    :connectionId,
                    :memberId,
                    :status,
                    :totalPageCount,
                    :processedPageCount,
                    CAST(:startedAt AS TIMESTAMPTZ),
                    CAST(:completedAt AS TIMESTAMPTZ),
                    CAST(:lastHeartbeatAt AS TIMESTAMPTZ),
                    CAST(:createdAt AS TIMESTAMPTZ)
                )
                """)
                .param(
                        "workspaceId",
                        context.workspaceId()
                )
                .param(
                        "connectionId",
                        context.connectionId()
                )
                .param(
                        "memberId",
                        context.memberId()
                )
                .param(
                        "status",
                        status
                )
                .param(
                        "totalPageCount",
                        totalPageCount
                )
                .param(
                        "processedPageCount",
                        processedPageCount
                )
                .param(
                        "startedAt",
                        startedAt
                )
                .param(
                        "completedAt",
                        completedAt
                )
                .param(
                        "lastHeartbeatAt",
                        "RUNNING".equals(status) ? startedAt : null
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .update();

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    private NotionImportRun activeImportRun(
            TestContext context,
            NotionImportStatus status
    ) {
        return NotionImportRun.create(
                context.workspaceId(),
                context.connectionId(),
                context.memberId(),
                status,
                null,
                0,
                status == NotionImportStatus.RUNNING ? CREATED_AT.plusSeconds(1) : null,
                null,
                CREATED_AT
        );
    }

    private NotionImportRun saveAndReload(
            NotionImportRun importRun,
            long memberId
    ) {
        NotionImportRun savedImportRun = importRunRepository.save(importRun);
        entityManager.flush();
        entityManager.clear();
        return importRunRepository.findVisibleByIdAndMemberId(
                savedImportRun.getId(),
                memberId
        )
                .orElseThrow();
    }

    private TestContext saveContext(String nickname) {
        String shortenedNickname = nickname.substring(
                0,
                Math.min(
                        nickname.length(),
                        10
                )
        );
        long memberId = saveMember(shortenedNickname);
        long workspaceId = saveWorkspace(shortenedNickname + " 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );
        long connectionId = saveConnection(
                workspaceId,
                memberId
        );
        return new TestContext(
                memberId,
                workspaceId,
                connectionId
        );
    }

    private long saveMember(String nickname) {
        return jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES (:nickname, NULL)
                RETURNING id
                """)
                .param(
                        "nickname",
                        nickname
                )
                .query(Long.class)
                .single();
    }

    private long saveWorkspace(String name) {
        return jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES (:name, CAST(:createdAt AS TIMESTAMPTZ))
                RETURNING id
                """)
                .param(
                        "name",
                        name
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private void saveWorkspaceMember(
            long workspaceId,
            long memberId,
            String role
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, :role, CAST(:joinedAt AS TIMESTAMPTZ))
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "role",
                        role
                )
                .param(
                        "joinedAt",
                        CREATED_AT.toString()
                )
                .update();
    }

    private long saveConnection(
            long workspaceId,
            long authorizingMemberId
    ) {
        return jdbcClient.sql("""
                INSERT INTO content_source_connections (
                    workspace_id,
                    provider,
                    access_credential_ciphertext,
                    refresh_credential_ciphertext,
                    external_source_id,
                    external_source_name,
                    external_source_icon,
                    provider_connection_id,
                    authorization_owner_type,
                    authorization_owner_id,
                    external_template_id,
                    provider_request_id,
                    authorizing_member_id,
                    created_at,
                    updated_at,
                    version
                ) VALUES (
                    :workspaceId,
                    'NOTION',
                    'encrypted-access-token',
                    NULL,
                    :notionWorkspaceId,
                    NULL,
                    NULL,
                    :botId,
                    'WORKSPACE',
                    NULL,
                    NULL,
                    NULL,
                    :authorizingMemberId,
                    CAST(:createdAt AS TIMESTAMPTZ),
                    CAST(:createdAt AS TIMESTAMPTZ),
                    0
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "notionWorkspaceId",
                        "notion-workspace-" + workspaceId
                )
                .param(
                        "botId",
                        "bot-" + workspaceId
                )
                .param(
                        "authorizingMemberId",
                        authorizingMemberId
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private static Stream<Arguments> invalidConstraintCases() {
        return Stream.of(
                Arguments.of(
                        "unknown-status",
                        "UNKNOWN",
                        10,
                        0,
                        null,
                        null
                ),
                Arguments.of(
                        "invalid-page-count",
                        "RUNNING",
                        10,
                        11,
                        CREATED_AT.plusSeconds(1)
                                .toString(),
                        null
                ),
                Arguments.of(
                        "pending-with-started-at",
                        "PENDING",
                        null,
                        0,
                        CREATED_AT.plusSeconds(1)
                                .toString(),
                        null
                ),
                Arguments.of(
                        "pending-with-completed-at",
                        "PENDING",
                        null,
                        0,
                        null,
                        CREATED_AT.plusSeconds(2)
                                .toString()
                ),
                Arguments.of(
                        "running-without-started-at",
                        "RUNNING",
                        null,
                        0,
                        null,
                        null
                ),
                Arguments.of(
                        "running-with-completed-at",
                        "RUNNING",
                        null,
                        0,
                        CREATED_AT.plusSeconds(1)
                                .toString(),
                        CREATED_AT.plusSeconds(2)
                                .toString()
                ),
                Arguments.of(
                        "completed-without-started-at",
                        "COMPLETED",
                        10,
                        10,
                        null,
                        CREATED_AT.plusSeconds(2)
                                .toString()
                ),
                Arguments.of(
                        "completed-without-completed-at",
                        "COMPLETED",
                        10,
                        10,
                        CREATED_AT.plusSeconds(1)
                                .toString(),
                        null
                ),
                Arguments.of(
                        "failed-without-started-at",
                        "FAILED",
                        10,
                        4,
                        null,
                        CREATED_AT.plusSeconds(2)
                                .toString()
                ),
                Arguments.of(
                        "failed-without-completed-at",
                        "FAILED",
                        10,
                        4,
                        CREATED_AT.plusSeconds(1)
                                .toString(),
                        null
                ),
                Arguments.of(
                        "completed-before-started-at",
                        "COMPLETED",
                        10,
                        10,
                        CREATED_AT.plusSeconds(2)
                                .toString(),
                        CREATED_AT.plusSeconds(1)
                                .toString()
                ),
                Arguments.of(
                        "failed-before-started-at",
                        "FAILED",
                        10,
                        4,
                        CREATED_AT.plusSeconds(2)
                                .toString(),
                        CREATED_AT.plusSeconds(1)
                                .toString()
                )
        );
    }

    private record TestContext(
            long memberId,
            long workspaceId,
            long connectionId
    ) {
    }
}
