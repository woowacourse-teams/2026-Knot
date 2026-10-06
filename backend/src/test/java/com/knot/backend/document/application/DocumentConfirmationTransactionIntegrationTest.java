package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.application.dto.result.DocumentConfirmationResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.domain.DocumentStatus;
import com.knot.backend.document.infrastructure.DocumentConfirmationQueryAdapter;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.WorkspaceLeaveService;
import com.knot.backend.workspace.application.WorkspaceOwnershipTransferService;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("integration")
@ActiveProfiles("dev")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
class DocumentConfirmationTransactionIntegrationTest {
    private static final Instant NOW = DocumentFixtures.CREATED_AT.plusSeconds(60);
    @Autowired
    private DocumentConfirmationCommandService service;
    @Autowired
    private WorkspaceLeaveService leave;
    @Autowired
    private WorkspaceOwnershipTransferService transfer;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private Clock clock;
    @MockitoSpyBean
    private DocumentConfirmationQueryAdapter query;
    private DocumentFixtures fixtures;
    private long workspaceId;
    private long firstId;
    private long secondId;
    private long documentId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        jdbc.sql("TRUNCATE TABLE members, workspaces RESTART IDENTITY CASCADE")
                .update();
        fixtures = new DocumentFixtures(jdbc);
        firstId = fixtures.saveMember("첫 대상");
        secondId = fixtures.saveMember("둘째 대상");
        workspaceId = fixtures.saveWorkspace();
        fixtures.join(
                workspaceId,
                firstId
        );
        fixtures.join(
                workspaceId,
                secondId
        );
        long recordingId = fixtures.saveRecording(
                workspaceId,
                firstId,
                1000
        );
        long transcriptId = fixtures.saveTranscript(recordingId);
        documentId = fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                fixtures.saveJob(
                        transcriptId,
                        "SUCCEEDED"
                ),
                "정책"
        );
        fixtures.target(
                documentId,
                firstId,
                null
        );
        fixtures.target(
                documentId,
                secondId,
                null
        );
    }

    @Test
    @DisplayName("확인 기록을 flush한 집계와 마지막 확인의 보관을 실제 DB에 커밋한다")
    void confirm_success_committedResult() {
        // given
        DocumentConfirmationResult first = service.confirm(
                workspaceId,
                firstId,
                documentId
        );
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        // when
        DocumentConfirmationResult last = service.confirm(
                workspaceId,
                secondId,
                documentId
        );
        DocumentConfirmationResult repeated = service.confirm(
                workspaceId,
                firstId,
                documentId
        );

        // then
        assertThat(first.documentStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(
                first.confirmationSummary()
                        .pendingCount()
        ).isEqualTo(1);
        assertThat(last.documentStatus()).isEqualTo(DocumentStatus.ARCHIVED);
        assertThat(repeated.confirmedAt()).isEqualTo(NOW);
        assertThat(repeated.archivedAt()).isEqualTo(NOW.plusSeconds(60));
        assertArchived(
                2,
                0
        );
    }

    @Test
    @DisplayName("집계 실패는 이미 flush한 확인 기록도 롤백한다")
    void confirm_failure_rollsBackFlushedConfirmation() {
        // given
        String before = fixtures.snapshot();
        doThrow(new IllegalStateException("집계 장애")).when(query)
                .findSummary(
                        anyLong(),
                        anyLong(),
                        anyLong()
                );

        // when & then
        assertThatThrownBy(
                () -> service.confirm(
                        workspaceId,
                        firstId,
                        documentId
                )
        ).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(fixtures.snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("서로 다른 마지막 두 대상이 동시에 확인해도 미확인 0이면 보관된다")
    void confirm_success_concurrentLastTargets() throws Exception {
        // given
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<DocumentConfirmationResult> first = pool.submit(() -> {
                start.await();
                return service.confirm(
                        workspaceId,
                        firstId,
                        documentId
                );
            });
            Future<DocumentConfirmationResult> second = pool.submit(() -> {
                start.await();
                return service.confirm(
                        workspaceId,
                        secondId,
                        documentId
                );
            });

            // when
            start.countDown();
            DocumentConfirmationResult firstResult = first.get(
                    10,
                    TimeUnit.SECONDS
            );
            DocumentConfirmationResult secondResult = second.get(
                    10,
                    TimeUnit.SECONDS
            );

            // then
            assertThat(
                    firstResult.confirmationSummary()
                            .pendingCount()
                            + secondResult.confirmationSummary()
                                    .pendingCount()
            ).isEqualTo(1);
            assertArchived(
                    2,
                    0
            );
        }
    }

    @Test
    @DisplayName("같은 대상의 동시 확인도 최초 시각과 확인 한 건을 유지한다")
    void confirm_success_concurrentSameTarget() throws Exception {
        // given
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<DocumentConfirmationResult> first = pool.submit(() -> {
                start.await();
                return service.confirm(
                        workspaceId,
                        firstId,
                        documentId
                );
            });
            Future<DocumentConfirmationResult> second = pool.submit(() -> {
                start.await();
                return service.confirm(
                        workspaceId,
                        firstId,
                        documentId
                );
            });

            // when
            start.countDown();
            DocumentConfirmationResult firstResult = first.get(
                    10,
                    TimeUnit.SECONDS
            );
            DocumentConfirmationResult secondResult = second.get(
                    10,
                    TimeUnit.SECONDS
            );

            // then
            assertThat(secondResult.confirmedAt()).isEqualTo(firstResult.confirmedAt());
            assertThat(
                    secondResult.confirmationSummary()
                            .confirmedCount()
            ).isEqualTo(1);
            assertThat(secondResult.documentStatus()).isEqualTo(DocumentStatus.DRAFT);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("일반 탈퇴와 OWNER 승계 후 탈퇴는 마지막 미확인 대상을 제외하고 보관한다")
    void departure_success_lastPendingTarget(boolean ownershipTransfer) {
        // given
        service.confirm(
                workspaceId,
                firstId,
                documentId
        );
        prepareDeparture(ownershipTransfer);

        // when
        depart(
                ownershipTransfer,
                secondId,
                firstId
        );

        // then
        assertArchived(
                1,
                1
        );
        assertThat(targetCount()).isEqualTo(2);
        assertThat(
                jdbc.sql(
                        "SELECT confirmed_at FROM document_confirmations WHERE document_id = :id AND member_id = :member"
                )
                        .param(
                                "id",
                                documentId
                        )
                        .param(
                                "member",
                                secondId
                        )
                        .query(Instant.class)
                        .single()
        ).isNull();
    }

    @Test
    @DisplayName("미확인 대상이 남으면 탈퇴 후에도 DRAFT이며 확인한 탈퇴자는 confirmed를 유지한다")
    void departure_success_preservesPendingAndConfirmed() {
        // given
        service.confirm(
                workspaceId,
                firstId,
                documentId
        );

        // when
        leave.leave(
                firstId,
                workspaceId
        );

        // then
        assertThat(storedStatus()).isEqualTo("DRAFT");
        assertThat(summary().confirmedCount()).isEqualTo(1);
        assertThat(summary().pendingCount()).isEqualTo(1);
        assertThat(targetCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("혼자 남은 OWNER 탈퇴의 Workspace 삭제와 문서 보관을 함께 저장한다")
    void departure_success_lastOwnerDeletesWorkspace() {
        // given
        leave.leave(
                firstId,
                workspaceId
        );
        makeOwner(secondId);

        // when
        leave.leave(
                secondId,
                workspaceId
        );

        // then
        assertArchived(
                0,
                2
        );
        assertThat(
                jdbc.sql("SELECT deleted_at FROM workspaces WHERE id = :id")
                        .param(
                                "id",
                                workspaceId
                        )
                        .query(Instant.class)
                        .single()
        ).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("보관 처리 실패는 일반 탈퇴와 OWNER 승계까지 같은 트랜잭션에서 롤백한다")
    void departure_failure_rollsBackMembership(boolean ownershipTransfer) {
        // given
        service.confirm(
                workspaceId,
                firstId,
                documentId
        );
        prepareDeparture(ownershipTransfer);
        String before = fixtures.snapshot();
        doThrow(new IllegalStateException("보관 집계 장애")).when(query)
                .findSummary(
                        anyLong(),
                        anyLong(),
                        anyLong()
                );

        // when & then
        assertThatThrownBy(
                () -> depart(
                        ownershipTransfer,
                        secondId,
                        firstId
                )
        ).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(fixtures.snapshot()).isEqualTo(before);
        assertThat(
                jdbc.sql("SELECT left_at FROM workspace_members WHERE workspace_id = :id AND member_id = :member")
                        .param(
                                "id",
                                workspaceId
                        )
                        .param(
                                "member",
                                secondId
                        )
                        .query(Instant.class)
                        .single()
        ).isNull();
        if (ownershipTransfer) {
            assertThat(
                    jdbc.sql("SELECT role FROM workspace_members WHERE workspace_id = :id AND member_id = :member")
                            .param(
                                    "id",
                                    workspaceId
                            )
                            .param(
                                    "member",
                                    secondId
                            )
                            .query(String.class)
                            .single()
            ).isEqualTo("OWNER");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("확인 중 일반 탈퇴와 OWNER 승계는 실제 Workspace 잠금을 기다린 뒤 보관한다")
    void confirm_success_concurrentDeparture(boolean ownershipTransfer) throws Exception {
        // given
        prepareDeparture(ownershipTransfer);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(
                    10,
                    TimeUnit.SECONDS
            )) {
                throw new IllegalStateException("확인 트랜잭션 대기 시간 초과");
            }
            return invocation.callRealMethod();
        }).when(query)
                .findSummary(
                        workspaceId,
                        documentId,
                        firstId
                );
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<DocumentConfirmationResult> confirming = pool.submit(
                    () -> service.confirm(
                            workspaceId,
                            firstId,
                            documentId
                    )
            );
            assertThat(
                    entered.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
            Future<?> departing = pool.submit(
                    () -> depart(
                            ownershipTransfer,
                            secondId,
                            firstId
                    )
            );
            try {
                // when
                assertDatabaseLockWait();
            } finally {
                release.countDown();
            }
            confirming.get(
                    10,
                    TimeUnit.SECONDS
            );
            departing.get(
                    10,
                    TimeUnit.SECONDS
            );

            // then
            assertArchived(
                    1,
                    1
            );
        } finally {
            release.countDown();
        }
    }

    @Test
    @DisplayName("탈퇴 커밋 뒤 도착한 확인은 권한 거절되며 대상 기록을 보존한다")
    void confirm_failure_afterDeparture() {
        // given
        leave.leave(
                firstId,
                workspaceId
        );
        String before = fixtures.snapshot();

        // when & then
        assertThatThrownBy(
                () -> service.confirm(
                        workspaceId,
                        firstId,
                        documentId
                )
        ).isInstanceOf(WorkspaceException.class);
        assertThat(fixtures.snapshot()).isEqualTo(before);
        assertThat(targetCount()).isEqualTo(2);
    }

    private void assertDatabaseLockWait() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            long waiting = jdbc.sql(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND cardinality(pg_blocking_pids(pid)) > 0"
            )
                    .query(Long.class)
                    .single();
            if (waiting > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("PostgreSQL 잠금 대기를 관측하지 못함");
    }

    private void prepareDeparture(boolean ownershipTransfer) {
        if (ownershipTransfer) {
            makeOwner(secondId);
        }
    }

    private void makeOwner(long memberId) {
        jdbc.sql("UPDATE workspace_members SET role = 'OWNER' WHERE workspace_id = :id AND member_id = :member")
                .param(
                        "id",
                        workspaceId
                )
                .param(
                        "member",
                        memberId
                )
                .update();
    }

    private void depart(
            boolean ownershipTransfer,
            long memberId,
            long successorId
    ) {
        if (ownershipTransfer) {
            transfer.transferOwnership(
                    memberId,
                    workspaceId,
                    successorId
            );
            return;
        }
        leave.leave(
                memberId,
                workspaceId
        );
    }

    private long targetCount() {
        return jdbc.sql("SELECT count(*) FROM document_confirmations WHERE document_id = :id")
                .param(
                        "id",
                        documentId
                )
                .query(Long.class)
                .single();
    }

    private String storedStatus() {
        return jdbc.sql("SELECT status FROM documents WHERE id = :id")
                .param(
                        "id",
                        documentId
                )
                .query(String.class)
                .single();
    }

    private DocumentConfirmationSummaryResult summary() {
        return query.findSummary(
                workspaceId,
                documentId,
                firstId
        )
                .orElseThrow()
                .summary();
    }

    private void assertArchived(
            int confirmed,
            int excluded
    ) {
        assertThat(storedStatus()).isEqualTo("ARCHIVED");
        assertThat(
                jdbc.sql("SELECT archived_at FROM documents WHERE id = :id")
                        .param(
                                "id",
                                documentId
                        )
                        .query(Instant.class)
                        .single()
        ).isNotNull();
        assertThat(
                query.findSummary(
                        workspaceId,
                        documentId,
                        firstId
                )
                        .orElseThrow()
                        .summary()
                        .confirmedCount()
        ).isEqualTo(confirmed);
        assertThat(
                query.findSummary(
                        workspaceId,
                        documentId,
                        firstId
                )
                        .orElseThrow()
                        .summary()
                        .pendingCount()
        ).isZero();
        assertThat(
                query.findSummary(
                        workspaceId,
                        documentId,
                        firstId
                )
                        .orElseThrow()
                        .summary()
                        .excludedCount()
        ).isEqualTo(excluded);
    }
}
