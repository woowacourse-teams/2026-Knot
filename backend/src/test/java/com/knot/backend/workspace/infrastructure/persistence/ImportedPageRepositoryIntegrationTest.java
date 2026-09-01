package com.knot.backend.workspace.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.ImportedPage;
import com.knot.backend.workspace.domain.ImportedPageMetadata;
import com.knot.backend.workspace.domain.ImportedPageRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@Import({TestcontainersConfiguration.class, ImportedPageRepositoryAdapter.class})
@DataJpaTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "com.knot.backend.workspace.infrastructure.persistence.ImportedPageQueryStatementInspector")
class ImportedPageRepositoryIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @Autowired
    private ImportedPageRepository importedPageRepository;
    @Autowired
    private ImportedPageJpaRepository importedPageJpaRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void clearCapturedSql() {
        ImportedPageQueryStatementInspector.clear();
    }

    @DisplayName("발행된 Import Run의 Page metadata만 position과 ID 오름차순으로 조회한다")
    @Test
    void findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc_success() {
        // given
        TestContext context = saveContext("Knot 팀");
        TestContext otherContext = saveContext("다른 팀");
        long runningRunId = saveImportRun(
                context.workspaceId(),
                context.connectionId(),
                context.memberId(),
                "RUNNING"
        );
        ImportedPage firstPage = savePage(
                context.workspaceId(),
                context.importRunId(),
                "first",
                null,
                "첫 Page",
                0
        );
        ImportedPage tiedPage = savePage(
                context.workspaceId(),
                context.importRunId(),
                "tied",
                null,
                "같은 순서 Page",
                0
        );
        ImportedPage lastPage = savePage(
                context.workspaceId(),
                context.importRunId(),
                "last",
                firstPage.getExternalPageId(),
                "마지막 Page",
                2
        );
        savePage(
                context.workspaceId(),
                runningRunId,
                "running",
                null,
                "실행 중 Page",
                0
        );
        savePage(
                otherContext.workspaceId(),
                otherContext.importRunId(),
                "other",
                null,
                "다른 Workspace Page",
                0
        );
        publishImportRun(
                context.workspaceId(),
                context.importRunId()
        );
        publishImportRun(
                otherContext.workspaceId(),
                otherContext.importRunId()
        );
        entityManager.flush();
        entityManager.clear();

        // when
        List<ImportedPageMetadata> result = importedPageRepository
                .findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(context.workspaceId());

        // then
        assertThat(result).extracting(
                ImportedPageMetadata::id,
                ImportedPageMetadata::title,
                ImportedPageMetadata::parentId,
                ImportedPageMetadata::hasParentReference,
                ImportedPageMetadata::position
        )
                .containsExactly(
                        tuple(
                                firstPage.getId(),
                                "첫 Page",
                                null,
                                false,
                                0
                        ),
                        tuple(
                                tiedPage.getId(),
                                "같은 순서 Page",
                                null,
                                false,
                                0
                        ),
                        tuple(
                                lastPage.getId(),
                                "마지막 Page",
                                firstPage.getId(),
                                true,
                                2
                        )
                );
    }

    @DisplayName("완료되지 않은 Import Run은 공개할 수 없다")
    @Test
    void publishImportRun_failure_nonCompletedRun() {
        // given
        TestContext context = saveContext("Knot 팀");
        long runningRunId = saveImportRun(
                context.workspaceId(),
                context.connectionId(),
                context.memberId(),
                "RUNNING"
        );

        // when
        ThrowingCallable action = () -> publishImportRun(
                context.workspaceId(),
                runningRunId
        );

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("공개된 Import Run의 완료 상태는 변경할 수 없다")
    @Test
    void updateImportRun_failure_publishedRunStatusChange() {
        // given
        TestContext context = saveContext("Knot 팀");
        publishImportRun(
                context.workspaceId(),
                context.importRunId()
        );

        // when
        ThrowingCallable action = () -> jdbcClient.sql("""
                UPDATE content_import_runs
                SET status = 'FAILED'
                WHERE id = :importRunId
                """)
                .param(
                        "importRunId",
                        context.importRunId()
                )
                .update();

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("부모 Page와 자식 Page의 Import Run이 다르면 저장할 수 없다")
    @Test
    void save_failure_parentImportRunMismatch() {
        // given
        TestContext context = saveContext("Knot 팀");
        long runningRunId = saveImportRun(
                context.workspaceId(),
                context.connectionId(),
                context.memberId(),
                "RUNNING"
        );
        ImportedPage parentPage = savePage(
                context.workspaceId(),
                context.importRunId(),
                "parent",
                null,
                "부모",
                0
        );
        entityManager.flush();
        ImportedPage childPage = importedPage(
                context.workspaceId(),
                runningRunId,
                "child",
                parentPage.getExternalPageId(),
                "자식",
                0
        );

        // when
        ThrowingCallable action = () -> importedPageJpaRepository.saveAndFlush(childPage);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("부모 Page와 자식 Page의 Workspace가 다르면 저장할 수 없다")
    @Test
    void save_failure_parentTenantMismatch() {
        // given
        TestContext context = saveContext("Knot 팀");
        TestContext otherContext = saveContext("다른 팀");
        ImportedPage parentPage = savePage(
                context.workspaceId(),
                context.importRunId(),
                "parent",
                null,
                "부모",
                0
        );
        entityManager.flush();
        ImportedPage childPage = importedPage(
                otherContext.workspaceId(),
                otherContext.importRunId(),
                "child",
                parentPage.getExternalPageId(),
                "자식",
                0
        );

        // when
        ThrowingCallable action = () -> importedPageJpaRepository.saveAndFlush(childPage);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("존재하지 않는 부모 Page를 참조하면 저장할 수 없다")
    @Test
    void save_failure_missingParent() {
        // given
        TestContext context = saveContext("Knot 팀");
        ImportedPage childPage = importedPage(
                context.workspaceId(),
                context.importRunId(),
                "child",
                "missing-parent",
                "자식",
                0
        );

        // when
        ThrowingCallable action = () -> importedPageJpaRepository.saveAndFlush(childPage);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("Page 저장 제약과 published run 조회 순서 인덱스를 제공한다")
    @Test
    void schema_success_constraintsAndQueryIndex() {
        // given
        String indexName = "idx_imported_pages_workspace_run_order";

        // when
        String indexDefinition = jdbcClient.sql("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = current_schema()
                    AND tablename = 'imported_pages'
                    AND indexname = :indexName
                """)
                .param(
                        "indexName",
                        indexName
                )
                .query(String.class)
                .single();

        // then
        assertThat(indexDefinition).contains("(workspace_id, import_run_id, \"position\", id)");
    }

    @DisplayName("Page metadata 조회 쿼리는 markdown_content 컬럼을 선택하지 않는다")
    @Test
    void query_success_doesNotSelectMarkdownContent() {
        // given
        TestContext context = saveContext("본문 비조회 팀");
        publishImportRun(
                context.workspaceId(),
                context.importRunId()
        );
        savePage(
                context.workspaceId(),
                context.importRunId(),
                "large-content",
                null,
                "큰 본문 Page",
                "큰 본문 ".repeat(100_000),
                0
        );
        entityManager.flush();
        entityManager.clear();
        ImportedPageQueryStatementInspector.clear();

        // when
        List<ImportedPageMetadata> result = importedPageRepository
                .findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(context.workspaceId());

        // then
        assertThat(result).singleElement()
                .extracting(ImportedPageMetadata::title)
                .isEqualTo("큰 본문 Page");
        assertThat(ImportedPageQueryStatementInspector.selectsFromImportedPages()).singleElement()
                .asString()
                .doesNotContainIgnoringCase("markdown_content");
    }

    private ImportedPage savePage(
            long workspaceId,
            long importRunId,
            String externalPageId,
            String parentExternalPageId,
            String title,
            int position
    ) {
        return savePage(
                workspaceId,
                importRunId,
                externalPageId,
                parentExternalPageId,
                title,
                "# " + title,
                position
        );
    }

    private ImportedPage savePage(
            long workspaceId,
            long importRunId,
            String externalPageId,
            String parentExternalPageId,
            String title,
            String markdownContent,
            int position
    ) {
        ImportedPage importedPage = importedPage(
                workspaceId,
                importRunId,
                externalPageId,
                parentExternalPageId,
                title,
                markdownContent,
                position
        );
        entityManager.persist(importedPage);
        entityManager.flush();
        return importedPage;
    }

    private ImportedPage importedPage(
            long workspaceId,
            long importRunId,
            String externalPageId,
            String parentExternalPageId,
            String title,
            int position
    ) {
        return importedPage(
                workspaceId,
                importRunId,
                externalPageId,
                parentExternalPageId,
                title,
                "# " + title,
                position
        );
    }

    private ImportedPage importedPage(
            long workspaceId,
            long importRunId,
            String externalPageId,
            String parentExternalPageId,
            String title,
            String markdownContent,
            int position
    ) {
        return ImportedPage.create(
                workspaceId,
                importRunId,
                externalPageId,
                parentExternalPageId,
                title,
                markdownContent,
                position,
                "https://content.example/pages/" + externalPageId,
                CREATED_AT,
                CREATED_AT
        );
    }

    private TestContext saveContext(String workspaceName) {
        long memberId = saveMember(workspaceName);
        long workspaceId = saveWorkspace(workspaceName);
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );
        long connectionId = saveConnection(
                workspaceId,
                memberId
        );
        long importRunId = saveImportRun(
                workspaceId,
                connectionId,
                memberId,
                "COMPLETED"
        );
        return new TestContext(
                workspaceId,
                memberId,
                connectionId,
                importRunId
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
                    external_source_id,
                    provider_connection_id,
                    authorization_owner_type,
                    authorizing_member_id,
                    created_at,
                    updated_at,
                    version
                ) VALUES (
                    :workspaceId,
                    'NOTION',
                    'encrypted-access-token',
                    :notionWorkspaceId,
                    :botId,
                    'WORKSPACE',
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

    private long saveImportRun(
            long workspaceId,
            long connectionId,
            long memberId,
            String status
    ) {
        return jdbcClient.sql("""
                INSERT INTO content_import_runs (
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
                    1,
                    1,
                    CAST(:startedAt AS TIMESTAMPTZ),
                    CAST(:completedAt AS TIMESTAMPTZ),
                    CAST(:lastHeartbeatAt AS TIMESTAMPTZ),
                    CAST(:createdAt AS TIMESTAMPTZ)
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "connectionId",
                        connectionId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "status",
                        status
                )
                .param(
                        "startedAt",
                        CREATED_AT.plusSeconds(1)
                                .toString()
                )
                .param(
                        "completedAt",
                        status.equals("COMPLETED")
                                ? CREATED_AT.plusSeconds(2)
                                        .toString()
                                : null
                )
                .param(
                        "lastHeartbeatAt",
                        status.equals("RUNNING")
                                ? CREATED_AT.plusSeconds(1)
                                        .toString()
                                : null
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private void publishImportRun(
            long workspaceId,
            long importRunId
    ) {
        jdbcClient.sql("""
                INSERT INTO imported_page_publications (workspace_id, published_import_run_id, published_at)
                VALUES (:workspaceId, :importRunId, CAST(:publishedAt AS TIMESTAMPTZ))
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "importRunId",
                        importRunId
                )
                .param(
                        "publishedAt",
                        CREATED_AT.plusSeconds(3)
                                .toString()
                )
                .update();
    }

    private record TestContext(
            long workspaceId,
            long memberId,
            long connectionId,
            long importRunId
    ) {
    }
}
