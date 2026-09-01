package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.dto.result.ClaimedNotionImportRun;
import com.knot.backend.workspace.application.dto.result.NotionImportRecoveryResult;
import com.knot.backend.workspace.domain.NotionImportRun;
import com.knot.backend.workspace.domain.NotionImportRunRepository;
import com.knot.backend.workspace.domain.NotionImportStatus;
import com.knot.backend.workspace.domain.NotionPage;
import com.knot.backend.workspace.domain.NotionPageMetadata;
import com.knot.backend.workspace.domain.NotionPageRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotionImportWorkerPersistenceIntegrationTest {
    private final NotionImportRunLifecycleService lifecycleService;
    private final NotionImportStaleRecoveryService staleRecoveryService;
    private final NotionImportSnapshotStagingService stagingService;
    private final NotionImportPublicationService publicationService;
    private final NotionImportRunRepository importRunRepository;
    @MockitoSpyBean
    private final NotionPageRepository notionPageRepository;
    private final JdbcClient jdbcClient;

    NotionImportWorkerPersistenceIntegrationTest(
            NotionImportRunLifecycleService lifecycleService,
            NotionImportStaleRecoveryService staleRecoveryService,
            NotionImportSnapshotStagingService stagingService,
            NotionImportPublicationService publicationService,
            NotionImportRunRepository importRunRepository,
            NotionPageRepository notionPageRepository,
            JdbcClient jdbcClient
    ) {
        this.lifecycleService = lifecycleService;
        this.staleRecoveryService = staleRecoveryService;
        this.stagingService = stagingService;
        this.publicationService = publicationService;
        this.importRunRepository = importRunRepository;
        this.notionPageRepository = notionPageRepository;
        this.jdbcClient = jdbcClient;
    }

    @DisplayName("동시 작업자는 같은 PENDING Import Run을 한 번만 선점한다")
    @Test
    void claimNext_success_onlyOneConcurrentWorkerClaimsRun() throws Exception {
        // given
        failAllActiveImportRuns();
        TestContext context = saveContext("동시 선점");
        NotionImportRun pendingImportRun = saveImportRun(
                context,
                NotionImportStatus.PENDING,
                null,
                0,
                null,
                null,
                Instant.now()
                        .minusSeconds(1)
        );
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Callable<Optional<ClaimedNotionImportRun>> claim = () -> {
            barrier.await(
                    5,
                    TimeUnit.SECONDS
            );
            return lifecycleService.claimNext();
        };

        try {
            // when
            List<Optional<ClaimedNotionImportRun>> results = awaitResults(
                    executorService,
                    claim,
                    claim
            );

            // then
            assertThat(
                    results.stream()
                            .flatMap(Optional::stream)
                            .toList()
            ).singleElement()
                    .extracting(ClaimedNotionImportRun::importRunId)
                    .isEqualTo(pendingImportRun.getId());
            assertThat(importRunStatus(pendingImportRun.getId())).isEqualTo(NotionImportStatus.RUNNING);
        } finally {
            executorService.shutdownNow();
        }
    }

    @DisplayName("기준 시각을 넘긴 PENDING과 RUNNING만 FAILED로 회수한다")
    @Test
    void recover_success_onlyStaleActiveRunsFail() {
        // given
        failAllActiveImportRuns();
        Instant now = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);
        TestContext stalePendingContext = saveContext("오래된 대기");
        TestContext staleRunningContext = saveContext("오래된 실행");
        TestContext freshPendingContext = saveContext("새 대기");
        TestContext freshRunningContext = saveContext("새 실행");
        NotionImportRun stalePending = saveImportRun(
                stalePendingContext,
                NotionImportStatus.PENDING,
                null,
                0,
                null,
                null,
                now.minus(Duration.ofMinutes(10))
        );
        Instant staleStartedAt = now.minus(Duration.ofMinutes(40));
        NotionImportRun staleRunning = saveImportRun(
                staleRunningContext,
                NotionImportStatus.RUNNING,
                null,
                0,
                staleStartedAt,
                null,
                now.minus(Duration.ofHours(1))
        );
        NotionImportRun freshPending = saveImportRun(
                freshPendingContext,
                NotionImportStatus.PENDING,
                null,
                0,
                null,
                null,
                now.minus(Duration.ofMinutes(1))
        );
        NotionImportRun freshRunning = saveImportRun(
                freshRunningContext,
                NotionImportStatus.RUNNING,
                null,
                0,
                now.minus(Duration.ofMinutes(1)),
                null,
                now.minus(Duration.ofMinutes(2))
        );

        // when
        NotionImportRecoveryResult result = staleRecoveryService.recover(
                Duration.ofMinutes(5),
                Duration.ofMinutes(30),
                10
        );

        // then
        assertThat(result.pendingCount()).isEqualTo(1);
        assertThat(result.runningCount()).isEqualTo(1);
        assertThat(importRunStatus(stalePending.getId())).isEqualTo(NotionImportStatus.FAILED);
        assertThat(importRunStatus(staleRunning.getId())).isEqualTo(NotionImportStatus.FAILED);
        assertThat(importRunStatus(freshPending.getId())).isEqualTo(NotionImportStatus.PENDING);
        assertThat(importRunStatus(freshRunning.getId())).isEqualTo(NotionImportStatus.RUNNING);
        assertThat(importRunStartedAt(stalePending.getId())).isEqualTo(importRunCompletedAt(stalePending.getId()));
        assertThat(importRunStartedAt(staleRunning.getId())).isEqualTo(staleStartedAt);

        NotionImportRun nextImportRun = saveImportRun(
                stalePendingContext,
                NotionImportStatus.PENDING,
                null,
                0,
                null,
                null,
                now
        );
        assertThat(nextImportRun.getId()).isPositive();
    }

    @DisplayName("완성된 새 Snapshot만 공개하고 이전 Run과 Page를 보존한다")
    @Test
    void publish_success_switchesPointerAndPreservesPreviousSnapshot() {
        // given
        TestContext context = saveContext("공개 전환");
        Instant now = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);
        NotionImportRun previousImportRun = saveImportRun(
                context,
                NotionImportStatus.COMPLETED,
                1,
                1,
                now.minus(Duration.ofMinutes(10)),
                now.minus(Duration.ofMinutes(9)),
                now.minus(Duration.ofMinutes(11))
        );
        savePage(
                context.workspaceId(),
                previousImportRun.getId(),
                "previous",
                null,
                "이전 Page",
                0,
                now.minus(Duration.ofMinutes(10))
        );
        publishImportRun(
                context.workspaceId(),
                previousImportRun.getId(),
                now.minus(Duration.ofMinutes(9))
        );
        NotionImportRun newImportRun = saveImportRun(
                context,
                NotionImportStatus.RUNNING,
                null,
                0,
                now.minus(Duration.ofMinutes(1)),
                null,
                now.minus(Duration.ofMinutes(2))
        );
        stagingService.prepare(
                newImportRun.getId(),
                context.workspaceId(),
                2
        );
        Long newParentPageId = stagingService.stagePage(
                newImportRun.getId(),
                context.workspaceId(),
                "new-parent",
                null,
                "새 부모",
                "# 새 부모",
                0,
                "https://www.notion.so/new-parent"
        );
        stagingService.stagePage(
                newImportRun.getId(),
                context.workspaceId(),
                "new-child",
                newParentPageId,
                "새 자식",
                "# 새 자식",
                1,
                "https://www.notion.so/new-child"
        );

        // when
        publicationService.publish(newImportRun.getId());

        // then
        assertThat(importRunStatus(newImportRun.getId())).isEqualTo(NotionImportStatus.COMPLETED);
        assertThat(publishedImportRunId(context.workspaceId())).isEqualTo(newImportRun.getId());
        assertThat(countImportRuns(context.workspaceId())).isEqualTo(2);
        assertThat(countPages(context.workspaceId())).isEqualTo(3);
        assertThat(
                notionPageRepository.findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(context.workspaceId())
        ).extracting(NotionPageMetadata::title)
                .containsExactly(
                        "새 부모",
                        "새 자식"
                );
    }

    @DisplayName("Page 저장이 실패하면 해당 Page와 진행률을 함께 rollback한다")
    @Test
    void stagePage_failure_rollsBackPageAndProgress() {
        // given
        TestContext context = saveContext("저장 실패");
        TestContext otherContext = saveContext("다른 저장");
        Instant now = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);
        NotionImportRun importRun = saveImportRun(
                context,
                NotionImportStatus.RUNNING,
                null,
                0,
                now.minusSeconds(1),
                null,
                now.minusSeconds(2)
        );
        NotionImportRun otherImportRun = saveImportRun(
                otherContext,
                NotionImportStatus.COMPLETED,
                1,
                1,
                now.minus(Duration.ofMinutes(2)),
                now.minus(Duration.ofMinutes(1)),
                now.minus(Duration.ofMinutes(3))
        );
        NotionPage otherPage = savePage(
                otherContext.workspaceId(),
                otherImportRun.getId(),
                "other-parent",
                null,
                "다른 부모",
                0,
                now
        );
        stagingService.prepare(
                importRun.getId(),
                context.workspaceId(),
                1
        );

        // when
        Throwable thrown = catchThrowable(
                () -> stagingService.stagePage(
                        importRun.getId(),
                        context.workspaceId(),
                        "invalid-child",
                        otherPage.getId(),
                        "잘못된 자식",
                        "# 잘못된 자식",
                        0,
                        "https://www.notion.so/invalid-child"
                )
        );

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(processedPageCount(importRun.getId())).isZero();
        assertThat(countPagesForImportRun(importRun.getId())).isZero();
        assertThat(importRunStatus(importRun.getId())).isEqualTo(NotionImportStatus.RUNNING);
    }

    @DisplayName("publication pointer 저장이 실패하면 COMPLETED 전이를 함께 rollback한다")
    @Test
    void publish_failure_rollsBackCompletionAndKeepsPreviousPublication() {
        // given
        TestContext context = saveContext("공개 실패");
        Instant now = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);
        NotionImportRun previousImportRun = saveImportRun(
                context,
                NotionImportStatus.COMPLETED,
                1,
                1,
                now.minus(Duration.ofMinutes(10)),
                now.minus(Duration.ofMinutes(9)),
                now.minus(Duration.ofMinutes(11))
        );
        savePage(
                context.workspaceId(),
                previousImportRun.getId(),
                "previous",
                null,
                "이전 Page",
                0,
                now.minus(Duration.ofMinutes(10))
        );
        publishImportRun(
                context.workspaceId(),
                previousImportRun.getId(),
                now.minus(Duration.ofMinutes(9))
        );
        NotionImportRun newImportRun = saveImportRun(
                context,
                NotionImportStatus.RUNNING,
                null,
                0,
                now.minus(Duration.ofMinutes(1)),
                null,
                now.minus(Duration.ofMinutes(2))
        );
        stagingService.prepare(
                newImportRun.getId(),
                context.workspaceId(),
                1
        );
        stagingService.stagePage(
                newImportRun.getId(),
                context.workspaceId(),
                "new",
                null,
                "새 Page",
                "# 새 Page",
                0,
                "https://www.notion.so/new"
        );
        doThrow(new DataIntegrityViolationException("forced publication failure")).when(notionPageRepository)
                .publish(
                        eq(context.workspaceId()),
                        eq(newImportRun.getId()),
                        any(Instant.class)
                );

        // when
        Throwable thrown = catchThrowable(() -> publicationService.publish(newImportRun.getId()));

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(importRunStatus(newImportRun.getId())).isEqualTo(NotionImportStatus.RUNNING);
        assertThat(publishedImportRunId(context.workspaceId())).isEqualTo(previousImportRun.getId());
    }

    private List<Optional<ClaimedNotionImportRun>> awaitResults(
            ExecutorService executorService,
            Callable<Optional<ClaimedNotionImportRun>> first,
            Callable<Optional<ClaimedNotionImportRun>> second
    ) throws Exception {
        Future<Optional<ClaimedNotionImportRun>> firstResult = executorService.submit(first);
        Future<Optional<ClaimedNotionImportRun>> secondResult = executorService.submit(second);
        return List.of(
                firstResult.get(
                        10,
                        TimeUnit.SECONDS
                ),
                secondResult.get(
                        10,
                        TimeUnit.SECONDS
                )
        );
    }

    private TestContext saveContext(String label) {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(
                        0,
                        8
                );
        long memberId = jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES (:nickname, NULL)
                RETURNING id
                """)
                .param(
                        "nickname",
                        label.substring(
                                0,
                                Math.min(
                                        label.length(),
                                        8
                                )
                        ) + suffix
                )
                .query(Long.class)
                .single();
        long workspaceId = jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES (:name, CAST(:createdAt AS TIMESTAMPTZ))
                RETURNING id
                """)
                .param(
                        "name",
                        label + suffix
                )
                .param(
                        "createdAt",
                        Instant.now()
                                .toString()
                )
                .query(Long.class)
                .single();
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, 'OWNER', CAST(:joinedAt AS TIMESTAMPTZ))
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
                        "joinedAt",
                        Instant.now()
                                .toString()
                )
                .update();
        long connectionId = jdbcClient.sql("""
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
                    :externalSourceId,
                    :providerConnectionId,
                    'WORKSPACE',
                    :memberId,
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
                        "externalSourceId",
                        "notion-workspace-" + suffix
                )
                .param(
                        "providerConnectionId",
                        "bot-" + suffix
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "createdAt",
                        Instant.now()
                                .toString()
                )
                .query(Long.class)
                .single();
        return new TestContext(
                memberId,
                workspaceId,
                connectionId
        );
    }

    private NotionImportRun saveImportRun(
            TestContext context,
            NotionImportStatus status,
            Integer totalPageCount,
            int processedPageCount,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt
    ) {
        return importRunRepository.save(
                NotionImportRun.create(
                        context.workspaceId(),
                        context.connectionId(),
                        context.memberId(),
                        status,
                        totalPageCount,
                        processedPageCount,
                        startedAt,
                        completedAt,
                        createdAt
                )
        );
    }

    private NotionPage savePage(
            long workspaceId,
            long importRunId,
            String notionPageId,
            Long parentPageId,
            String title,
            int position,
            Instant createdAt
    ) {
        return notionPageRepository.save(
                NotionPage.create(
                        workspaceId,
                        importRunId,
                        notionPageId,
                        parentPageId,
                        title,
                        "# " + title,
                        position,
                        "https://www.notion.so/" + notionPageId,
                        createdAt,
                        createdAt
                )
        );
    }

    private void failAllActiveImportRuns() {
        Instant failedAt = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);
        jdbcClient.sql("""
                UPDATE notion_import_runs
                SET status = 'FAILED',
                    started_at = COALESCE(started_at, CAST(:failedAt AS TIMESTAMPTZ)),
                    completed_at = CAST(:failedAt AS TIMESTAMPTZ)
                WHERE status IN ('PENDING', 'RUNNING')
                """)
                .param(
                        "failedAt",
                        failedAt.toString()
                )
                .update();
    }

    private NotionImportStatus importRunStatus(long importRunId) {
        return jdbcClient.sql("""
                SELECT status
                FROM notion_import_runs
                WHERE id = :importRunId
                """)
                .param(
                        "importRunId",
                        importRunId
                )
                .query(String.class)
                .single()
                .transform(NotionImportStatus::valueOf);
    }

    private Instant importRunStartedAt(long importRunId) {
        return importRunTimestamp(
                importRunId,
                "started_at"
        );
    }

    private Instant importRunCompletedAt(long importRunId) {
        return importRunTimestamp(
                importRunId,
                "completed_at"
        );
    }

    private Instant importRunTimestamp(
            long importRunId,
            String columnName
    ) {
        return jdbcClient.sql("""
                SELECT %s
                FROM notion_import_runs
                WHERE id = :importRunId
                """.formatted(columnName))
                .param(
                        "importRunId",
                        importRunId
                )
                .query(Instant.class)
                .single();
    }

    private long publishedImportRunId(long workspaceId) {
        return jdbcClient.sql("""
                SELECT published_import_run_id
                FROM notion_page_publications
                WHERE workspace_id = :workspaceId
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
    }

    private void publishImportRun(
            long workspaceId,
            long importRunId,
            Instant publishedAt
    ) {
        jdbcClient.sql("""
                INSERT INTO notion_page_publications (
                    workspace_id,
                    published_import_run_id,
                    published_at
                ) VALUES (
                    :workspaceId,
                    :importRunId,
                    CAST(:publishedAt AS TIMESTAMPTZ)
                )
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
                        publishedAt.toString()
                )
                .update();
    }

    private long countImportRuns(long workspaceId) {
        return countByWorkspaceId(
                "notion_import_runs",
                workspaceId
        );
    }

    private long countPages(long workspaceId) {
        return countByWorkspaceId(
                "notion_pages",
                workspaceId
        );
    }

    private long countPagesForImportRun(long importRunId) {
        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM notion_pages
                WHERE import_run_id = :importRunId
                """)
                .param(
                        "importRunId",
                        importRunId
                )
                .query(Long.class)
                .single();
    }

    private int processedPageCount(long importRunId) {
        return jdbcClient.sql("""
                SELECT processed_page_count
                FROM notion_import_runs
                WHERE id = :importRunId
                """)
                .param(
                        "importRunId",
                        importRunId
                )
                .query(Integer.class)
                .single();
    }

    private long countByWorkspaceId(
            String tableName,
            long workspaceId
    ) {
        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM %s
                WHERE workspace_id = :workspaceId
                """.formatted(tableName))
                .param(
                        "workspaceId",
                        workspaceId
                )
                .query(Long.class)
                .single();
    }

    private record TestContext(
            long memberId,
            long workspaceId,
            long connectionId
    ) {
    }
}
