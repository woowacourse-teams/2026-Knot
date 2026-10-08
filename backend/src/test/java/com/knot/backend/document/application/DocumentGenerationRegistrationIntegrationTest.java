package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.application.dto.result.DocumentGenerationRegistrationResult;
import com.knot.backend.document.application.dto.result.DocumentTopicClassificationResult;
import com.knot.backend.document.application.dto.result.DocumentGenerationJobItemResult;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import com.knot.backend.document.domain.DocumentTopicRegistrationState;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.recording.domain.RecordingException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.DataIntegrityViolationException;
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
    private DocumentClassificationResultService classifications;
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
    @DisplayName("분류 결과와 주제별 Job을 함께 커밋하고 재전송은 동결된 결과를 반환한다")
    void completeClassification_success_registeredTopics() {
        // given
        long jobId = startClassification();
        DocumentTopicClassificationResult topics = new DocumentTopicClassificationResult(
                List.of(
                        "검색",
                        "알림"
                )
        );
        // when
        DocumentGenerationRegistrationResult result = classifications.completeClassification(
                workspaceId,
                jobId,
                1,
                topics
        );
        DocumentGenerationRegistrationResult repeated = classifications.completeClassification(
                workspaceId,
                jobId,
                1,
                topics
        );
        // then
        assertThat(result).isEqualTo(repeated);
        assertThat(result.registrationState()).isEqualTo(DocumentTopicRegistrationState.TOPICS_REGISTERED);
        assertThat(result.generationJobIds()).hasSize(2);
        assertThat(count("document_generation_jobs")).isEqualTo(3);
        assertThat(
                jdbc.sql("SELECT topic FROM document_generation_batch_topics ORDER BY position")
                        .query(String.class)
                        .list()
        ).containsExactly(
                "검색",
                "알림"
        );
        assertThat(status(jobId)).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("분류와 등록 사이 또는 Job 저장 이후 장애는 모든 변경을 롤백한다")
    void completeClassification_failure_registrationRollback() {
        // given
        long jobId = startClassification();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("등록 후 장애");
        }).when(jobs)
                .flush();
        // when & then
        assertThatThrownBy(
                () -> classifications.completeClassification(
                        workspaceId,
                        jobId,
                        1,
                        new DocumentTopicClassificationResult(
                                List.of(
                                        "검색",
                                        "알림"
                                )
                        )
                )
        ).isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThat(count("document_generation_jobs")).isEqualTo(1);
        assertThat(count("document_generation_batch_topics")).isZero();
        assertThat(status(jobId)).isEqualTo("RUNNING");
        assertThat(accept().registrationState()).isEqualTo(DocumentTopicRegistrationState.WAITING_CLASSIFICATION);
    }

    @Test
    @DisplayName("내용 없음은 원문 정리 후 지연 통지가 와도 새 작업을 만들지 않는다")
    void completeClassification_success_retainedNoContent() {
        // given
        long jobId = startClassification();
        classifications.completeClassification(
                workspaceId,
                jobId,
                1,
                new DocumentTopicClassificationResult(List.of())
        );
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        classifications.completeClassification(
                workspaceId,
                jobId,
                1,
                new DocumentTopicClassificationResult(List.of())
        );
        jdbc.sql("DELETE FROM document_generation_jobs")
                .update();
        jdbc.sql("UPDATE document_generation_batches SET transcript_id = NULL")
                .update();
        jdbc.sql("DELETE FROM transcripts")
                .update();
        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
        // when
        DocumentGenerationRegistrationResult delayed = accept();
        // then
        assertThat(delayed.registrationState()).isEqualTo(DocumentTopicRegistrationState.NO_CONTENT);
        assertThat(cleanupTime()).isEqualTo(NOW);
        assertThat(count("document_generation_jobs")).isZero();
    }

    @Test
    @DisplayName("분류 실패는 기존 재시도 API로 같은 Job을 재접수하고 이전 결과는 거절한다")
    void failClassification_success_retryAndRejectStaleResult() {
        // given
        long jobId = startClassification();
        classifications.failClassification(
                workspaceId,
                jobId,
                1
        );
        Instant firstExpiry = jobTime(
                jobId,
                "expires_at"
        );
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        classifications.failClassification(
                workspaceId,
                jobId,
                1
        );
        // when
        retries.retry(
                workspaceId,
                memberId,
                jobId
        );
        startJob(jobId);
        // then
        assertThat(
                jobTime(
                        jobId,
                        "expires_at"
                )
        ).isEqualTo(firstExpiry);
        assertThatThrownBy(
                () -> classifications.completeClassification(
                        workspaceId,
                        jobId,
                        1,
                        new DocumentTopicClassificationResult(List.of("검색"))
                )
        ).isInstanceOf(DocumentException.class);
        assertThat(
                classifications.completeClassification(
                        workspaceId,
                        jobId,
                        2,
                        new DocumentTopicClassificationResult(List.of("검색"))
                )
                        .generationJobIds()
        ).hasSize(1);
    }

    @Test
    @DisplayName("다른 결과 재전송과 생성 Job의 분류 결과 반영은 거절한다")
    void completeClassification_failure_conflictingResultAndStage() {
        // given
        long classifier = startClassification();
        long generator = classifications.completeClassification(
                workspaceId,
                classifier,
                1,
                new DocumentTopicClassificationResult(List.of("검색"))
        )
                .generationJobIds()
                .getFirst();
        // when & then
        assertThatThrownBy(
                () -> classifications.completeClassification(
                        workspaceId,
                        classifier,
                        1,
                        new DocumentTopicClassificationResult(List.of("알림"))
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> classifications.failClassification(
                        workspaceId,
                        generator,
                        1
                )
        ).isInstanceOf(DocumentException.class);
        assertThat(count("document_generation_jobs")).isEqualTo(2);
        assertThatThrownBy(
                () -> classifications.failClassification(
                        workspaceId,
                        classifier,
                        1
                )
        ).isInstanceOf(DocumentException.class);
        assertThat(status(classifier)).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("시작 전 분류 성공과 실패는 상태를 바꾸지 않는다")
    void completeClassification_failure_notRunning() {
        // given
        long classifier = accept().classificationJobId();
        // when & then
        assertThatThrownBy(
                () -> classifications.completeClassification(
                        workspaceId,
                        classifier,
                        1,
                        new DocumentTopicClassificationResult(List.of("검색"))
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> classifications.failClassification(
                        workspaceId,
                        classifier,
                        1
                )
        ).isInstanceOf(DocumentException.class);
        assertThat(status(classifier)).isEqualTo("QUEUED");
    }

    @Test
    @DisplayName("분류 결과의 동시 전송도 주제별 생성 Job을 한 번만 등록한다")
    void completeClassification_success_concurrent() throws Exception {
        // given
        long classifier = startClassification();
        DocumentTopicClassificationResult topics = new DocumentTopicClassificationResult(
                List.of(
                        "검색",
                        "알림"
                )
        );
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // when
            Future<DocumentGenerationRegistrationResult> first = executor.submit(() -> {
                start.await();
                return classifications.completeClassification(
                        workspaceId,
                        classifier,
                        1,
                        topics
                );
            });
            Future<DocumentGenerationRegistrationResult> second = executor.submit(() -> {
                start.await();
                return classifications.completeClassification(
                        workspaceId,
                        classifier,
                        1,
                        topics
                );
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
            assertThat(count("document_generation_jobs")).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("첫 주제 저장 후 장애가 나도 다음 실행에서 누락 없이 모든 주제를 등록한다")
    void completeClassification_success_replayAfterInterruptedRegistration() {
        // given
        long classifier = startClassification();
        AtomicInteger saves = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (saves.incrementAndGet() == 2) {
                throw new IllegalStateException("두 번째 주제 저장 후 장애");
            }
            return invocation.getArgument(0);
        }).when(jobs)
                .save(any(DocumentGenerationJob.class));
        DocumentTopicClassificationResult topics = new DocumentTopicClassificationResult(
                List.of(
                        "검색",
                        "알림"
                )
        );
        // when & then
        assertThatThrownBy(
                () -> classifications.completeClassification(
                        workspaceId,
                        classifier,
                        1,
                        topics
                )
        ).isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThat(count("document_generation_jobs")).isEqualTo(1);
        assertThat(count("document_generation_batch_topics")).isZero();
        doCallRealMethod().when(jobs)
                .save(any(DocumentGenerationJob.class));
        assertThat(
                classifications.completeClassification(
                        workspaceId,
                        classifier,
                        1,
                        topics
                )
                        .generationJobIds()
        ).hasSize(2);
    }

    @Test
    @DisplayName("주제 목록 flush 직후 장애도 등록 완료와 분류 성공을 남기지 않는다")
    void completeClassification_failure_topicsFlushRollback() {
        // given
        long classifier = startClassification();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("주제 목록 저장 직후 장애");
        }).when(batches)
                .saveAndFlush(any());
        // when & then
        assertThatThrownBy(
                () -> classifications.completeClassification(
                        workspaceId,
                        classifier,
                        1,
                        new DocumentTopicClassificationResult(List.of("검색"))
                )
        ).isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThat(count("document_generation_batch_topics")).isZero();
        assertThat(count("document_generation_jobs")).isEqualTo(1);
        assertThat(status(classifier)).isEqualTo("RUNNING");
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
    @DisplayName("삭제한 Workspace는 원문 접수와 분류 결과 모두 거절한다")
    void completeClassification_failure_deletedWorkspace() {
        // given
        long classifier = startClassification();
        jdbc.sql("UPDATE workspaces SET deleted_at = :time WHERE id = :id")
                .param(
                        "time",
                        Timestamp.from(NOW)
                )
                .param(
                        "id",
                        workspaceId
                )
                .update();
        // when & then
        assertThatThrownBy(this::accept).isInstanceOf(WorkspaceException.class);
        assertThatThrownBy(
                () -> classifications.completeClassification(
                        workspaceId,
                        classifier,
                        1,
                        new DocumentTopicClassificationResult(List.of("검색"))
                )
        ).isInstanceOf(WorkspaceException.class);
        assertThat(status(classifier)).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("등록한 주제가 같아도 서로 다른 녹음은 별도 생성 대상으로 유지한다")
    void completeClassification_success_sameTopicOtherRecording() {
        // given
        long firstClassifier = startClassification();
        long otherRecording = fixtures.saveRecording(
                workspaceId,
                memberId,
                10000
        );
        long otherTranscript = fixtures.saveTranscript(otherRecording);
        long secondClassifier = transactions.execute(
                status -> intake.acceptCompletedTranscript(
                        workspaceId,
                        otherRecording,
                        otherTranscript
                )
        )
                .classificationJobId();
        startJob(secondClassifier);
        DocumentTopicClassificationResult topics = new DocumentTopicClassificationResult(List.of("검색"));
        // when
        classifications.completeClassification(
                workspaceId,
                firstClassifier,
                1,
                topics
        );
        classifications.completeClassification(
                workspaceId,
                secondClassifier,
                1,
                topics
        );
        // then
        assertThat(count("document_generation_batches")).isEqualTo(2);
        assertThat(count("document_generation_jobs")).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"topic = '미등록 주제'", "stage = 'UNKNOWN'", "stage = 'CLASSIFICATION'",
            "transcript_id = 999999"})
    @DisplayName("DB도 미등록 주제·잘못된 단계·다른 입력 연결을 거절한다")
    void persist_failure_invalidJobTarget(String assignment) {
        // given
        long classifier = startClassification();
        long generator = classifications.completeClassification(
                workspaceId,
                classifier,
                1,
                new DocumentTopicClassificationResult(List.of("검색"))
        )
                .generationJobIds()
                .getFirst();
        // when & then
        assertThatThrownBy(
                () -> jdbc.sql("UPDATE document_generation_jobs SET " + assignment + " WHERE id = :id")
                        .param(
                                "id",
                                generator
                        )
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB도 분류 Job과 같은 주제 Job의 중복 생성을 거절한다")
    void persist_failure_duplicateJobs() {
        // given
        long classifier = startClassification();
        long generator = classifications.completeClassification(
                workspaceId,
                classifier,
                1,
                new DocumentTopicClassificationResult(List.of("검색"))
        )
                .generationJobIds()
                .getFirst();
        // when & then
        for (long source : List.of(
                classifier,
                generator
        )) {
            assertThatThrownBy(
                    () -> jdbc
                            .sql(
                                    """
                                            INSERT INTO document_generation_jobs (batch_id, transcript_id, stage, topic, status, created_at, updated_at)
                                            SELECT batch_id, transcript_id, stage, topic, 'QUEUED', created_at, updated_at
                                            FROM document_generation_jobs WHERE id = :id
                                            """
                            )
                            .param(
                                    "id",
                                    source
                            )
                            .update()
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("분류 대기 입력을 제거하거나 다른 녹음의 원문으로 바꾸지 못한다")
    void persist_failure_invalidBatchInput() {
        // given
        accept();
        long foreignRecording = fixtures.saveRecording(
                workspaceId,
                memberId,
                10000
        );
        long foreignTranscript = fixtures.saveTranscript(foreignRecording);
        // when & then
        assertThatThrownBy(
                () -> jdbc.sql("UPDATE document_generation_batches SET transcript_id = NULL")
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                () -> jdbc.sql("UPDATE document_generation_batches SET transcript_id = :id")
                        .param(
                                "id",
                                foreignTranscript
                        )
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
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

    @Test
    @DisplayName("생산자가 먼저 Workspace를 잠그면 삭제는 접수 커밋까지 기다리고 이후 결과는 거절된다")
    void acceptCompletedTranscript_success_workspaceDeletionWaits() throws Exception {
        // given
        jdbc.sql("DELETE FROM transcripts")
                .update();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch finishProducer = new CountDownLatch(1);
        AtomicLong producerPid = new AtomicLong();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<DocumentGenerationRegistrationResult> producer = executor
                    .submit(() -> transactions.execute(status -> {
                        workspaces.findByIdForUpdate(workspaceId)
                                .orElseThrow();
                        producerPid.set(
                                jdbc.sql("SELECT pg_backend_pid()")
                                        .query(Long.class)
                                        .single()
                        );
                        locked.countDown();
                        awaitSignal(finishProducer);
                        long saved = fixtures.saveTranscript(recordingId);
                        saveSegment(saved);
                        return intake.acceptCompletedTranscript(
                                workspaceId,
                                recordingId,
                                saved
                        );
                    }));
            try {
                assertThat(
                        locked.await(
                                10,
                                TimeUnit.SECONDS
                        )
                ).isTrue();
                Future<?> deletion = executor.submit(
                        () -> transactions.executeWithoutResult(
                                status -> workspaces.findByIdForUpdate(workspaceId)
                                        .orElseThrow()
                                        .delete(NOW)
                        )
                );
                // when
                awaitDatabaseWaiter(producerPid.get());
                finishProducer.countDown();
                DocumentGenerationRegistrationResult result = producer.get(
                        10,
                        TimeUnit.SECONDS
                );
                deletion.get(
                        10,
                        TimeUnit.SECONDS
                );
                // then
                assertThat(count("transcript_segments")).isEqualTo(1);
                assertThat(count("document_generation_jobs")).isEqualTo(1);
                assertThatThrownBy(
                        () -> classifications.failClassification(
                                workspaceId,
                                result.classificationJobId(),
                                1
                        )
                ).isInstanceOf(WorkspaceException.class);
            } finally {
                finishProducer.countDown();
            }
        }
    }

    @Test
    @DisplayName("분류 실패도 본인 진행 목록과 같은 ID 재시도에 연결되고 성공 후 제외된다")
    void failClassification_success_existingJobList() {
        // given
        long classifier = startClassification();
        classifications.failClassification(
                workspaceId,
                classifier,
                1
        );
        // when
        List<DocumentGenerationJobItemResult> failed = jobList.findPage(
                workspaceId,
                memberId,
                20,
                null,
                NOW
        );
        retries.retry(
                workspaceId,
                memberId,
                classifier
        );
        startJob(classifier);
        classifications.completeClassification(
                workspaceId,
                classifier,
                2,
                new DocumentTopicClassificationResult(List.of())
        );
        // then
        assertThat(failed).extracting(DocumentGenerationJobItemResult::jobId)
                .containsExactly(classifier);
        assertThat(
                jobList.findPage(
                        workspaceId,
                        memberId,
                        20,
                        null,
                        NOW
                )
        ).isEmpty();
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
