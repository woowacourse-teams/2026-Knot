package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.application.dto.result.DocumentGenerationJobRetryResult;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@ActiveProfiles("dev")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
class DocumentGenerationJobRetryTransactionIntegrationTest {
    private static final Instant NOW = DocumentFixtures.CREATED_AT.plusSeconds(120);

    @Autowired
    private DocumentGenerationJobRetryService service;
    @MockitoSpyBean
    private DocumentGenerationJobRepository jobs;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private Clock clock;
    private DocumentFixtures fixtures;
    private long memberId;
    private long workspaceId;
    private long transcriptId;
    private long jobId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        jdbc.sql("TRUNCATE TABLE members, workspaces RESTART IDENTITY CASCADE")
                .update();
        fixtures = new DocumentFixtures(jdbc);
        memberId = fixtures.saveMember("재시도 대상");
        workspaceId = fixtures.saveWorkspace();
        fixtures.join(
                workspaceId,
                memberId
        );
        long recordingId = fixtures.saveRecording(
                workspaceId,
                memberId,
                120000
        );
        transcriptId = fixtures.saveTranscript(recordingId);
        jobId = fixtures.saveJob(
                transcriptId,
                "FAILED"
        );
    }

    @Test
    @DisplayName("202 결과는 Job 상태·횟수의 실제 커밋과 일치한다")
    void retry_success_committedResult() {
        // when
        DocumentGenerationJobRetryResult result = service.retry(
                workspaceId,
                memberId,
                jobId
        );

        // then
        assertThat(result.status()).isEqualTo(DocumentGenerationJobStatus.QUEUED);
        assertQueuedOnce();
    }

    @Test
    @DisplayName("Job 변경을 flush한 뒤 장애가 나도 상태와 횟수는 모두 롤백된다")
    void retry_failure_rollbackAfterJobFlush() {
        // given
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Job flush 이후 장애");
        }).when(jobs)
                .flush();

        // when & then
        assertThatThrownBy(
                () -> service.retry(
                        workspaceId,
                        memberId,
                        jobId
                )
        ).isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Job flush 이후 장애");
        assertUnchangedFailure();
    }

    @Test
    @DisplayName("같은 Job 동시 요청은 한 번만 접수하고 나머지는 RETRY_NOT_ALLOWED다")
    void retry_success_concurrentSingleAcceptance() throws Exception {
        // given
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // when
            Future<String> first = executor.submit(() -> {
                barrier.await(
                        5,
                        TimeUnit.SECONDS
                );
                return retryOutcome();
            });
            Future<String> second = executor.submit(() -> {
                barrier.await(
                        5,
                        TimeUnit.SECONDS
                );
                return retryOutcome();
            });

            // then
            assertThat(
                    List.of(
                            first.get(
                                    10,
                                    TimeUnit.SECONDS
                            ),
                            second.get(
                                    10,
                                    TimeUnit.SECONDS
                            )
                    )
            ).containsExactlyInAnyOrder(
                    "ACCEPTED",
                    "RETRY_NOT_ALLOWED"
            );
            assertQueuedOnce();
        }
    }

    @Test
    @DisplayName("잠금 대기 중 만료되면 대기 이전 시각으로 접수하지 않는다")
    void retry_failure_expiredWhileWaiting() throws Exception {
        // given
        Instant deadline = DocumentFixtures.CREATED_AT.plusSeconds(168 * 3600);
        when(clock.instant()).thenReturn(deadline.minusSeconds(1));
        AtomicReference<Future<String>> pending = new AtomicReference<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // when
            transactions.executeWithoutResult(status -> {
                int holderPid = lockRow(
                        "document_generation_jobs",
                        jobId
                );
                pending.set(executor.submit(this::retryOutcome));
                assertThat(
                        waitUntilBlocked(
                                holderPid,
                                pending.get()
                        )
                ).isTrue();
                when(clock.instant()).thenReturn(deadline);
            });

            // then
            assertThat(
                    pending.get()
                            .get(
                                    10,
                                    TimeUnit.SECONDS
                            )
            ).isEqualTo("RETRY_NOT_ALLOWED");
            assertUnchangedFailure();
        }
    }

    @Test
    @DisplayName("입력 원문 잠금 대기 뒤에도 최신 서버 시각으로 기한을 검사한다")
    void retry_failure_expiredWhileWaitingForInput() throws Exception {
        // given
        Instant deadline = DocumentFixtures.CREATED_AT.plusSeconds(168 * 3600);
        when(clock.instant()).thenReturn(deadline.minusSeconds(1));
        AtomicReference<Future<String>> pending = new AtomicReference<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // when
            transactions.executeWithoutResult(status -> {
                int holderPid = lockRow(
                        "transcripts",
                        transcriptId
                );
                pending.set(executor.submit(this::retryOutcome));
                assertThat(
                        waitUntilBlocked(
                                holderPid,
                                pending.get()
                        )
                ).isTrue();
                when(clock.instant()).thenReturn(deadline);
            });

            // then
            assertThat(
                    pending.get()
                            .get(
                                    10,
                                    TimeUnit.SECONDS
                            )
            ).isEqualTo("RETRY_NOT_ALLOWED");
            assertUnchangedFailure();
        }
    }

    @Test
    @DisplayName("재접수가 먼저 확정되면 만료 FAILED 정리는 QUEUED Job과 입력을 지우지 않는다")
    void retry_success_protectedFromExpiredCleanup() {
        // given
        Instant deadline = DocumentFixtures.CREATED_AT.plusSeconds(168 * 3600);
        when(clock.instant()).thenReturn(deadline.minusNanos(1000));

        // when
        service.retry(
                workspaceId,
                memberId,
                jobId
        );
        int deleted = jdbc.sql("""
                DELETE FROM document_generation_jobs
                WHERE id = :id AND status = 'FAILED' AND expires_at <= :now
                """)
                .param(
                        "id",
                        jobId
                )
                .param(
                        "now",
                        Timestamp.from(deadline)
                )
                .update();

        // then
        assertThat(deleted).isZero();
        assertQueuedOnce();
        assertThat(
                jdbc.sql("SELECT count(*) FROM transcripts WHERE id = :id")
                        .param(
                                "id",
                                transcriptId
                        )
                        .query(Long.class)
                        .single()
        ).isEqualTo(1);
    }

    @Test
    @DisplayName("재접수가 Job 잠금을 먼저 잡으면 대기한 정리는 커밋된 QUEUED를 보고 입력을 보호한다")
    void retry_success_cleanupWaitsForAcceptance() throws Exception {
        // given
        Instant deadline = DocumentFixtures.CREATED_AT.plusSeconds(168 * 3600);
        when(clock.instant()).thenReturn(deadline.minusNanos(1000));
        AtomicReference<Future<String>> retry = new AtomicReference<>();
        AtomicReference<Future<Integer>> cleanup = new AtomicReference<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // when
            transactions.executeWithoutResult(status -> {
                int inputHolder = lockRow(
                        "transcripts",
                        transcriptId
                );
                retry.set(executor.submit(this::retryOutcome));
                assertThat(
                        waitUntilBlocked(
                                inputHolder,
                                retry.get()
                        )
                ).isTrue();
                int retryHolder = jdbc.sql("""
                        SELECT pid FROM pg_stat_activity
                        WHERE pid <> pg_backend_pid() AND :holder = ANY(pg_blocking_pids(pid))
                        """)
                        .param(
                                "holder",
                                inputHolder
                        )
                        .query(Integer.class)
                        .single();
                cleanup.set(executor.submit(() -> transactions.execute(cleanupStatus -> {
                    lockRow(
                            "workspaces",
                            workspaceId
                    );
                    jobs.findByWorkspaceIdAndIdForUpdate(
                            workspaceId,
                            jobId
                    )
                            .orElseThrow();
                    return jdbc.sql("""
                            DELETE FROM document_generation_jobs
                            WHERE id = :id AND status = 'FAILED' AND expires_at <= :now
                            """)
                            .param(
                                    "id",
                                    jobId
                            )
                            .param(
                                    "now",
                                    Timestamp.from(deadline)
                            )
                            .update();
                })));
                assertThat(
                        waitUntilBlocked(
                                retryHolder,
                                cleanup.get()
                        )
                ).isTrue();
            });

            // then
            assertThat(
                    retry.get()
                            .get(
                                    10,
                                    TimeUnit.SECONDS
                            )
            ).isEqualTo("ACCEPTED");
            assertThat(
                    cleanup.get()
                            .get(
                                    10,
                                    TimeUnit.SECONDS
                            )
            ).isZero();
            assertQueuedOnce();
            assertThat(
                    jdbc.sql("SELECT count(*) FROM transcripts WHERE id = :id")
                            .param(
                                    "id",
                                    transcriptId
                            )
                            .query(Long.class)
                            .single()
            ).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("정리가 Job 잠금을 먼저 잡고 삭제하면 대기하던 재접수는 404다")
    void retry_failure_cleanupWins() throws Exception {
        // given
        when(clock.instant()).thenReturn(DocumentFixtures.CREATED_AT.plusSeconds(168 * 3600));
        AtomicReference<Future<String>> pending = new AtomicReference<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // when
            transactions.executeWithoutResult(status -> {
                int holderPid = lockRow(
                        "document_generation_jobs",
                        jobId
                );
                pending.set(executor.submit(this::retryOutcome));
                assertThat(
                        waitUntilBlocked(
                                holderPid,
                                pending.get()
                        )
                ).isTrue();
                jdbc.sql("DELETE FROM document_generation_jobs WHERE id = :id AND status = 'FAILED'")
                        .param(
                                "id",
                                jobId
                        )
                        .update();
            });

            // then
            assertThat(
                    pending.get()
                            .get(
                                    10,
                                    TimeUnit.SECONDS
                            )
            ).isEqualTo("DOCUMENT_GENERATION_JOB_NOT_FOUND");
            assertThat(
                    jdbc.sql("SELECT count(*) FROM document_generation_jobs WHERE id = :id")
                            .param(
                                    "id",
                                    jobId
                            )
                            .query(Long.class)
                            .single()
            ).isZero();
        }
    }

    @Test
    @DisplayName("탈퇴가 먼저 확정되면 잠금 대기 요청은 현재 멤버 검사에서 거절된다")
    void retry_failure_departureWins() throws Exception {
        // given
        AtomicReference<Future<String>> pending = new AtomicReference<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // when
            transactions.executeWithoutResult(status -> {
                int holderPid = lockRow(
                        "workspaces",
                        workspaceId
                );
                pending.set(executor.submit(this::retryOutcome));
                assertThat(
                        waitUntilBlocked(
                                holderPid,
                                pending.get()
                        )
                ).isTrue();
                fixtures.leave(
                        workspaceId,
                        memberId
                );
            });

            // then
            assertThat(
                    pending.get()
                            .get(
                                    10,
                                    TimeUnit.SECONDS
                            )
            ).isEqualTo("WORKSPACE_ACCESS_DENIED");
            assertUnchangedFailure();
        }
    }

    @Test
    @DisplayName("재실패는 기한만 갱신하고 사용자 3회·전체 회차를 유지한다")
    void retry_failure_fourthAttemptAfterRefailure() {
        // given
        for (int retry = 1; retry <= 3; retry++) {
            Instant acceptedAt = NOW.plusSeconds(retry * 2);
            when(clock.instant()).thenReturn(acceptedAt);
            assertThat(
                    service.retry(
                            workspaceId,
                            memberId,
                            jobId
                    )
                            .attemptCount()
            ).isEqualTo(retry + 1);
            transactions.executeWithoutResult(status -> {
                DocumentGenerationJob job = jobs.findByWorkspaceIdAndIdForUpdate(
                        workspaceId,
                        jobId
                )
                        .orElseThrow();
                job.recordFailure(acceptedAt.plusSeconds(1));
                jobs.flush();
                fixtures.synchronizeBatch(job.getBatchId());
            });
        }
        when(clock.instant()).thenReturn(NOW.plusSeconds(8));

        // when & then
        assertThatThrownBy(
                () -> service.retry(
                        workspaceId,
                        memberId,
                        jobId
                )
        ).isInstanceOf(DocumentException.class);
        assertThat(counter("attempt_count")).isEqualTo(4);
        assertThat(counter("user_retry_count")).isEqualTo(3);
        assertThat(userRetryCount()).isEqualTo(3);
        assertThat(
                jdbc.sql("SELECT expires_at FROM document_generation_jobs WHERE id = :id")
                        .param(
                                "id",
                                jobId
                        )
                        .query(Timestamp.class)
                        .single()
                        .toInstant()
        ).isEqualTo(NOW.plusSeconds(7 + 168 * 3600));
    }

    @Test
    @DisplayName("자동 시도 이력이 있는 Job도 사용자 한도는 별도로 증가한다")
    void retry_success_preserveAutomaticCounts() {
        // given
        jdbc.sql("UPDATE document_generation_jobs SET attempt_count = 9, automatic_retry_count = 8 WHERE id = :id")
                .param(
                        "id",
                        jobId
                )
                .update();

        // when
        assertThat(
                service.retry(
                        workspaceId,
                        memberId,
                        jobId
                )
                        .attemptCount()
        ).isEqualTo(10);

        // then
        assertThat(counter("user_retry_count")).isEqualTo(1);
        assertThat(counter("automatic_retry_count")).isEqualTo(8);
    }

    private String retryOutcome() {
        try {
            service.retry(
                    workspaceId,
                    memberId,
                    jobId
            );
            return "ACCEPTED";
        } catch (DocumentException exception) {
            return exception.getErrorCode()
                    .getCode();
        } catch (WorkspaceException exception) {
            return exception.getErrorCode()
                    .getCode();
        }
    }

    private int lockRow(
            String table,
            long id
    ) {
        return jdbc.sql("SELECT pg_backend_pid() FROM " + table + " WHERE id = :id FOR UPDATE")
                .param(
                        "id",
                        id
                )
                .query(Integer.class)
                .single();
    }

    private boolean waitUntilBlocked(
            int holderPid,
            Future<?> pending
    ) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (pending.isDone()) {
                return false;
            }
            if (jdbc.sql("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                        WHERE pid <> pg_backend_pid() AND :holderPid = ANY(pg_blocking_pids(pid)))
                    """)
                    .param(
                            "holderPid",
                            holderPid
                    )
                    .query(Boolean.class)
                    .single()) {
                return true;
            }
            awaitNextProbe();
        }
        return false;
    }

    private void awaitNextProbe() {
        try {
            TimeUnit.MILLISECONDS.sleep(20);
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
            throw new IllegalStateException(
                    "잠금 확인 중 인터럽트",
                    exception
            );
        }
    }

    private int counter(String column) {
        return jdbc.sql("SELECT " + column + " FROM document_generation_jobs WHERE id = :id")
                .param(
                        "id",
                        jobId
                )
                .query(Integer.class)
                .single();
    }

    private long userRetryCount() {
        return jdbc.sql("SELECT user_retry_count FROM document_generation_jobs WHERE id = :id")
                .param(
                        "id",
                        jobId
                )
                .query(Long.class)
                .single();
    }

    private String jobStatus() {
        return jdbc.sql("SELECT status FROM document_generation_jobs WHERE id = :id")
                .param(
                        "id",
                        jobId
                )
                .query(String.class)
                .single();
    }

    private void assertQueuedOnce() {
        assertThat(jobStatus()).isEqualTo("QUEUED");
        assertThat(counter("attempt_count")).isEqualTo(2);
        assertThat(counter("user_retry_count")).isEqualTo(1);
        assertThat(userRetryCount()).isEqualTo(1);
    }

    private void assertUnchangedFailure() {
        assertThat(jobStatus()).isEqualTo("FAILED");
        assertThat(counter("attempt_count")).isEqualTo(1);
        assertThat(counter("user_retry_count")).isZero();
        assertThat(userRetryCount()).isZero();
    }
}
