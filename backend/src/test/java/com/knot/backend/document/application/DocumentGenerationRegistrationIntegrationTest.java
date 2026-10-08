package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.application.dto.result.DocumentGenerationRegistrationResult;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import com.knot.backend.document.domain.DocumentTopicRegistrationState;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import com.knot.backend.recording.domain.RecordingException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@ActiveProfiles("dev")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
class DocumentGenerationRegistrationIntegrationTest {

    private static final Instant NOW = DocumentFixtures.CREATED_AT.plusSeconds(120);

    @Autowired
    private DocumentGenerationIntakeService intake;
    @Autowired
    private DocumentGenerationJobRetryService retries;
    @Autowired
    private DocumentGenerationJobListQuery jobList;
    @MockitoSpyBean
    private DocumentGenerationJobRepository jobs;
    @MockitoSpyBean
    private DocumentGenerationBatchRepository batches;
    @Autowired
    private WorkspaceRepository workspaces;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private Clock clock;
    private DocumentFixtures fixtures;
    private long workspaceId;
    private long memberId;
    private long recordingId;
    private long transcriptId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        jdbc.sql("TRUNCATE TABLE members, workspaces RESTART IDENTITY CASCADE")
                .update();
        fixtures = new DocumentFixtures(jdbc);
        memberId = fixtures.saveMember("녹음자");
        workspaceId = fixtures.saveWorkspace();
        fixtures.join(
                workspaceId,
                memberId
        );
        recordingId = fixtures.saveRecording(
                workspaceId,
                memberId,
                120000
        );
        transcriptId = fixtures.saveTranscript(recordingId);
    }

    @Test
    @DisplayName("접수는 분류 Job을 먼저 저장하고 반복 통지에 같은 식별자를 반환한다")
    void acceptCompletedTranscript_success_repeat() {
        // when
        DocumentGenerationRegistrationResult first = accept();
        DocumentGenerationRegistrationResult repeated = accept();
        // then
        assertThat(repeated).isEqualTo(first);
        assertThat(first.registrationState()).isEqualTo(DocumentTopicRegistrationState.WAITING_CLASSIFICATION);
        assertThat(first.classificationJobId()).isNotNull();
        assertThat(first.generationJobIds()).isEmpty();
        assertThat(count("document_generation_jobs")).isEqualTo(1);
    }

    @Test
    @DisplayName("원문 저장 트랜잭션이 없으면 접수하지 않는다")
    void acceptCompletedTranscript_failure_withoutProducerTransaction() {
        // when & then
        assertThatThrownBy(
                () -> intake.acceptCompletedTranscript(
                        workspaceId,
                        recordingId,
                        transcriptId
                )
        ).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count("document_generation_batches")).isZero();
    }

    @Test
    @DisplayName("접수 이후 생산자가 실패하면 원문과 접수 결과가 모두 롤백된다")
    void acceptCompletedTranscript_failure_producerRollback() {
        // given
        jdbc.sql("DELETE FROM transcripts")
                .update();
        // when & then
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            workspaces.findByIdForUpdate(workspaceId)
                    .orElseThrow();
            long saved = fixtures.saveTranscript(recordingId);
            saveSegment(saved);
            intake.acceptCompletedTranscript(
                    workspaceId,
                    recordingId,
                    saved
            );
            throw new IllegalStateException("생산자 저장 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("transcripts")).isZero();
        assertThat(count("transcript_segments")).isZero();
        assertThat(count("document_generation_batches")).isZero();
        assertThat(count("document_generation_jobs")).isZero();
    }

    @Test
    @DisplayName("같은 원문의 동시 접수도 Batch와 분류 Job을 하나만 만든다")
    void acceptCompletedTranscript_success_concurrent() throws Exception {
        // given
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // when
            Future<DocumentGenerationRegistrationResult> first = executor.submit(() -> {
                start.await();
                return accept();
            });
            Future<DocumentGenerationRegistrationResult> second = executor.submit(() -> {
                start.await();
                return accept();
            });
            // then
            assertThat(
                    first.get(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isEqualTo(
                    second.get(
                            10,
                            TimeUnit.SECONDS
                    )
            );
            assertThat(count("document_generation_batches")).isEqualTo(1);
            assertThat(count("document_generation_jobs")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("공백뿐인 성공 원문은 LLM 대기 Job 없이 내용 없음으로 보존한다")
    void acceptCompletedTranscript_success_emptyContent() {
        // given
        jdbc.sql("UPDATE transcripts SET content = :content WHERE id = :id")
                .param(
                        "content",
                        "\u00a0\u3000\n"
                )
                .param(
                        "id",
                        transcriptId
                )
                .update();
        // when
        DocumentGenerationRegistrationResult result = accept();
        // then
        assertThat(result.registrationState()).isEqualTo(DocumentTopicRegistrationState.NO_CONTENT);
        assertThat(result.classificationJobId()).isNull();
        assertThat(count("document_generation_jobs")).isZero();
        assertThat(cleanupTime()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("동일 녹음의 다른 원문과 다른 Workspace 입력은 접수하지 않는다")
    void acceptCompletedTranscript_failure_inputScope() {
        // given
        accept();
        long otherTranscript = fixtures.saveTranscript(recordingId);
        long otherWorkspace = fixtures.saveWorkspace();
        // when & then
        assertThatThrownBy(
                () -> transactions.executeWithoutResult(
                        status -> intake.acceptCompletedTranscript(
                                workspaceId,
                                recordingId,
                                otherTranscript
                        )
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> transactions.executeWithoutResult(
                        status -> intake.acceptCompletedTranscript(
                                otherWorkspace,
                                recordingId,
                                transcriptId
                        )
                )
        ).isInstanceOf(DocumentException.class);
        assertThat(count("document_generation_jobs")).isEqualTo(1);
    }

    @Test
    @DisplayName("녹음자가 탈퇴했어도 정상 종료한 녹음의 자동 접수는 유지한다")
    void acceptCompletedTranscript_success_recorderLeft() {
        // given
        fixtures.leave(
                workspaceId,
                memberId
        );
        // when & then
        assertThat(accept().classificationJobId()).isNotNull();
    }

    @Test
    @DisplayName("종료하지 않은 녹음은 접수하지 않는다")
    void acceptCompletedTranscript_failure_notEnded() {
        // given
        jdbc.sql(
                "UPDATE recording_sessions SET status = 'RECORDING', current_interval_started_at = last_seen_at, ended_at = NULL WHERE id = :id"
        )
                .param(
                        "id",
                        recordingId
                )
                .update();
        // when & then
        assertThatThrownBy(this::accept).isInstanceOf(RecordingException.class);
        assertThat(count("document_generation_batches")).isZero();
    }

    @Test
    @DisplayName("생산자의 원문·구간 저장과 접수는 하나의 실제 커밋으로 남는다")
    void acceptCompletedTranscript_success_producerCommit() {
        // given
        jdbc.sql("DELETE FROM transcripts")
                .update();
        // when
        DocumentGenerationRegistrationResult result = transactions.execute(status -> {
            workspaces.findByIdForUpdate(workspaceId)
                    .orElseThrow();
            long saved = fixtures.saveTranscript(recordingId);
            saveSegment(saved);
            return intake.acceptCompletedTranscript(
                    workspaceId,
                    recordingId,
                    saved
            );
        });
        // then
        assertThat(result.classificationJobId()).isNotNull();
        assertThat(count("transcripts")).isEqualTo(1);
        assertThat(count("transcript_segments")).isEqualTo(1);
        assertThat(count("document_generation_jobs")).isEqualTo(1);
    }

    private void saveSegment(long inputId) {
        jdbc.sql("""
                INSERT INTO transcript_segments (transcript_id, position, start_millis, text)
                VALUES (:id, 0, 1200, '실제 저장 발화')
                """)
                .param(
                        "id",
                        inputId
                )
                .update();
    }

    private void awaitSignal(CountDownLatch signal) {
        try {
            if (!signal.await(
                    10,
                    TimeUnit.SECONDS
            )) {
                throw new AssertionError("생산자 진행 신호를 받지 못했습니다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
            throw new AssertionError(exception);
        }
    }

    private void awaitDatabaseWaiter(long producerPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            boolean waiting = jdbc.sql("""
                    SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE :pid = ANY(pg_blocking_pids(pid)))
                    """)
                    .param(
                            "pid",
                            producerPid
                    )
                    .query(Boolean.class)
                    .single();
            if (waiting) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Workspace 삭제 트랜잭션의 실제 잠금 대기를 확인하지 못했습니다");
    }

    private DocumentGenerationRegistrationResult accept() {
        return transactions.execute(
                status -> intake.acceptCompletedTranscript(
                        workspaceId,
                        recordingId,
                        transcriptId
                )
        );
    }

    private long startClassification() {
        long jobId = accept().classificationJobId();
        startJob(jobId);
        return jobId;
    }

    private void startJob(long jobId) {
        transactions.executeWithoutResult(status -> {
            DocumentGenerationJob job = jobs.findByWorkspaceIdAndIdForUpdate(
                    workspaceId,
                    jobId
            )
                    .orElseThrow();
            job.startRunning(clock.instant());
        });
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    private String status(long jobId) {
        return jdbc.sql("SELECT status FROM document_generation_jobs WHERE id = :id")
                .param(
                        "id",
                        jobId
                )
                .query(String.class)
                .single();
    }

    private Instant jobTime(
            long jobId,
            String column
    ) {
        return jdbc.sql("SELECT " + column + " FROM document_generation_jobs WHERE id = :id")
                .param(
                        "id",
                        jobId
                )
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    private Instant cleanupTime() {
        return jdbc.sql("SELECT cleanup_requested_at FROM document_generation_batches")
                .query(Timestamp.class)
                .single()
                .toInstant();
    }
}
