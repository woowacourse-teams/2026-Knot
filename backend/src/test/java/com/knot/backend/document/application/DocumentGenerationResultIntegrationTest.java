package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.application.dto.query.DocumentListParameters;
import com.knot.backend.document.application.dto.result.DocumentCardResult;
import com.knot.backend.document.application.dto.result.DocumentTopicClassificationResult;
import com.knot.backend.document.domain.DocumentConfirmationRepository;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentRepository;
import com.knot.backend.document.domain.DocumentStatus;
import com.knot.backend.document.domain.MyConfirmationState;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.WorkspaceInvitationService;
import com.knot.backend.workspace.application.WorkspaceInvitationAcceptanceService;
import com.knot.backend.workspace.application.WorkspaceLeaveService;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
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
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.DataIntegrityViolationException;
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
class DocumentGenerationResultIntegrationTest {
    private static final Instant NOW = DocumentFixtures.CREATED_AT.plusSeconds(120);
    private static final DocumentGenerationResult RESULT = new DocumentGenerationResult(
            "검색 도입 조건",
            "조건을 충족한 뒤 개발한다.",
            "## 핵심 요약\n검색 개발 시점을 정했다.\n\n## 보류\n사용자가 1,000명을 넘으면 개발한다."
    );

    @Autowired
    private DocumentGenerationResultService results;
    @Autowired
    private DocumentGenerationIntakeService intake;
    @Autowired
    private DocumentClassificationResultService classifications;
    @Autowired
    private DocumentDetailService details;
    @Autowired
    private DocumentListService lists;
    @Autowired
    private DocumentConfirmationCommandService confirmationCommands;
    @Autowired
    private WorkspaceInvitationService invitations;
    @Autowired
    private WorkspaceInvitationAcceptanceService acceptance;
    @Autowired
    private WorkspaceLeaveService departures;
    @Autowired
    private WorkspaceRepository workspaces;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcClient jdbc;
    @MockitoSpyBean
    private DocumentRepository documents;
    @MockitoSpyBean
    private DocumentConfirmationRepository confirmations;
    @MockitoSpyBean
    private DocumentGenerationJobRepository jobs;
    @MockitoSpyBean
    private DocumentGenerationInputQuery inputs;
    @MockitoBean
    private Clock clock;
    private DocumentFixtures fixtures;
    private long workspaceId;
    private long memberId;
    private long recordingId;
    private long transcriptId;
    private long jobId;

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
        jdbc.sql("UPDATE workspace_members SET role = 'OWNER' WHERE workspace_id = :id")
                .param(
                        "id",
                        workspaceId
                )
                .update();
        recordingId = fixtures.saveRecording(
                workspaceId,
                memberId,
                120000
        );
        transcriptId = fixtures.saveTranscript(recordingId);
        long classifierId = transactions.execute(
                status -> intake.acceptCompletedTranscript(
                        workspaceId,
                        recordingId,
                        transcriptId
                )
        )
                .classificationJobId();
        startJob(classifierId);
        jobId = classifications.completeClassification(
                workspaceId,
                classifierId,
                1,
                new DocumentTopicClassificationResult(List.of("검색"))
        )
                .generationJobIds()
                .getFirst();
        startJob(jobId);
    }

    @Test
    @DisplayName("현재 멤버의 확인 대상과 DRAFT, Job 성공을 함께 커밋하고 즉시 조회한다")
    void completeGeneration_success_atomicCommit() {
        // given
        long teammate = fixtures.saveMember("팀원");
        fixtures.join(
                workspaceId,
                teammate
        );
        long departed = fixtures.saveMember("탈퇴자");
        fixtures.join(
                workspaceId,
                departed
        );
        fixtures.leave(
                workspaceId,
                departed
        );
        // when
        long documentId = complete();
        // then
        assertThat(targets(documentId)).containsExactly(
                memberId,
                teammate
        );
        assertThat(jobStatus()).isEqualTo("SUCCEEDED");
        assertThat(
                jdbc.sql("SELECT updated_at FROM document_generation_jobs WHERE id = :id")
                        .param(
                                "id",
                                jobId
                        )
                        .query(Instant.class)
                        .single()
        ).isEqualTo(NOW);
        assertThat(
                details.find(
                        workspaceId,
                        teammate,
                        documentId
                )
        ).satisfies(document -> {
            assertThat(document.status()).isEqualTo(DocumentStatus.DRAFT);
            assertThat(document.myConfirmationState()).isEqualTo(MyConfirmationState.PENDING);
            assertThat(
                    document.confirmationSummary()
                            .pendingCount()
            ).isEqualTo(2);
            assertThat(document.topic()).isEqualTo("검색");
            assertThat(document.title()).isEqualTo(RESULT.title());
            assertThat(document.content()).isEqualTo(RESULT.content());
            assertThat(document.sourceTranscriptId()).isEqualTo(transcriptId);
            assertThat(document.recordingSessionId()).isEqualTo(recordingId);
            assertThat(document.createdAt()).isEqualTo(NOW);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"document", "confirmation", "job"})
    @DisplayName("문서 삽입 이후·확인 대상 저장 중·Job 성공 저장 실패를 전체 롤백한다")
    void completeGeneration_failure_rollback(String failurePoint) {
        // given
        switch (failurePoint) {
            case "document" -> doAnswer(invocation -> {
                invocation.callRealMethod();
                throw new IllegalStateException("문서 저장 후 실패");
            }).when(documents)
                    .save(any());
            case "confirmation" -> doAnswer(invocation -> {
                invocation.callRealMethod();
                confirmations.flush();
                throw new IllegalStateException("확인 대상 저장 후 실패");
            }).when(confirmations)
                    .saveAll(any());
            case "job" -> doAnswer(invocation -> {
                invocation.callRealMethod();
                throw new IllegalStateException("Job flush 후 실패");
            }).when(jobs)
                    .flush();
            default -> throw new AssertionError("알 수 없는 실패 위치");
        }
        // when & then
        assertThatThrownBy(this::complete).isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertNoSavedDocument();
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "content", "null"})
    @DisplayName("필수 제목·본문이 없거나 결과가 없으면 문서를 저장하지 않는다")
    void completeGeneration_failure_invalidResult(String missing) {
        // given
        DocumentGenerationResult invalid = null;
        if (missing.equals("title")) {
            invalid = new DocumentGenerationResult(
                    "\u00a0\u3000",
                    null,
                    RESULT.content()
            );
        }
        if (missing.equals("content")) {
            invalid = new DocumentGenerationResult(
                    RESULT.title(),
                    null,
                    "\n "
            );
        }
        DocumentGenerationResult input = invalid;
        // when & then
        assertThatThrownBy(
                () -> results.completeGeneration(
                        workspaceId,
                        jobId,
                        1,
                        input
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE.getMessage());
        assertNoSavedDocument();
    }

    @Test
    @DisplayName("성공 재전달은 최초 문서·확인 대상·보관 상태를 유지한다")
    void completeGeneration_success_repeatedAfterArchive() {
        // given
        long documentId = complete();
        confirmationCommands.confirm(
                workspaceId,
                memberId,
                documentId
        );
        long newcomer = fixtures.saveMember("새 멤버");
        fixtures.join(
                workspaceId,
                newcomer
        );
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));
        // when
        long repeated = results.completeGeneration(
                workspaceId,
                jobId,
                1,
                null
        );
        // then
        assertThat(repeated).isEqualTo(documentId);
        assertThat(targets(documentId)).containsExactly(memberId);
        assertThat(
                details.find(
                        workspaceId,
                        newcomer,
                        documentId
                )
        ).satisfies(document -> {
            assertThat(document.status()).isEqualTo(DocumentStatus.ARCHIVED);
            assertThat(document.archivedAt()).isEqualTo(NOW);
            assertThat(document.createdAt()).isEqualTo(NOW);
            assertThat(document.content()).isEqualTo(RESULT.content());
            assertThat(document.myConfirmationState()).isEqualTo(MyConfirmationState.NOT_REQUIRED);
            assertThat(
                    document.confirmationSummary()
                            .confirmedCount()
            ).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("같은 시도의 동시 완료도 문서와 확인 대상을 하나만 생성한다")
    void completeGeneration_success_concurrent() throws Exception {
        // given
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // when
            Future<Long> first = executor.submit(() -> {
                start.await();
                return complete();
            });
            Future<Long> second = executor.submit(() -> {
                start.await();
                return complete();
            });
            // then
            long documentId = first.get(
                    10,
                    TimeUnit.SECONDS
            );
            assertThat(
                    second.get(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isEqualTo(documentId);
            assertThat(
                    jdbc.sql("SELECT count(*) FROM documents")
                            .query(Long.class)
                            .single()
            ).isEqualTo(1);
            assertThat(targets(documentId)).containsExactly(memberId);
        }
    }

    @Test
    @DisplayName("이전 시도의 결과는 현재 RUNNING 또는 SUCCEEDED Job을 변경하지 않는다")
    void completeGeneration_failure_staleAttempt() {
        // given
        transactions.executeWithoutResult(status -> {
            DocumentGenerationJob job = jobs.findByWorkspaceIdAndIdForUpdate(
                    workspaceId,
                    jobId
            )
                    .orElseThrow();
            job.recordFailure(NOW);
            job.retryByUser(NOW);
            job.startRunning(NOW);
        });
        // when & then
        assertThatThrownBy(this::complete).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT.getMessage());
        assertNoSavedDocument();
        long documentId = results.completeGeneration(
                workspaceId,
                jobId,
                2,
                RESULT
        );
        assertThatThrownBy(this::complete).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT.getMessage());
        assertThat(
                results.completeGeneration(
                        workspaceId,
                        jobId,
                        2,
                        RESULT
                )
        ).isEqualTo(documentId);
        assertThat(jobStatus()).isEqualTo("SUCCEEDED");
        assertThat(
                jdbc.sql("SELECT attempt_count FROM document_generation_jobs WHERE id = :id")
                        .param(
                                "id",
                                jobId
                        )
                        .query(Integer.class)
                        .single()
        ).isEqualTo(2);
    }

    @Test
    @DisplayName("성공 Job의 문서가 없으면 재생성하지 않고 저장 불일치로 거절한다")
    void completeGeneration_failure_successWithoutDocument() {
        // given
        transactions.executeWithoutResult(
                status -> jobs.findByWorkspaceIdAndIdForUpdate(
                        workspaceId,
                        jobId
                )
                        .orElseThrow()
                        .recordSuccess(NOW)
        );
        // when & then
        assertThatThrownBy(this::complete).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT.getMessage());
        assertThat(
                jdbc.sql("SELECT count(*) FROM documents")
                        .query(Long.class)
                        .single()
        ).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"join", "leave"})
    @DisplayName("가입·탈퇴가 먼저 커밋되면 잠금 대기 후 최신 멤버로 확인 대상을 고정한다")
    void completeGeneration_success_membershipCommitsFirst(String change) throws Exception {
        // given
        long teammate = fixtures.saveMember("변경할 멤버");
        Runnable mutation = membershipChange(
                change,
                teammate
        );
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicLong holderPid = new AtomicLong();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> membership = executor.submit(() -> transactions.executeWithoutResult(status -> {
                workspaces.findByIdForUpdate(workspaceId)
                        .orElseThrow();
                mutation.run();
                holderPid.set(databasePid());
                locked.countDown();
                awaitSignal(release);
            }));
            try {
                awaitSignal(locked);
                Future<Long> generation = executor.submit(this::complete);
                // when
                awaitDatabaseWaiter(holderPid.get());
                release.countDown();
                membership.get(
                        10,
                        TimeUnit.SECONDS
                );
                long documentId = generation.get(
                        10,
                        TimeUnit.SECONDS
                );
                // then
                if (change.equals("join")) {
                    assertThat(targets(documentId)).containsExactly(
                            memberId,
                            teammate
                    );
                } else {
                    assertThat(targets(documentId)).containsExactly(memberId);
                }
            } finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"join", "leave"})
    @DisplayName("생성이 먼저 커밋되면 가입은 대상에 추가하지 않고 탈퇴는 기존 대상에서 제외한다")
    void completeGeneration_success_generationCommitsFirst(String change) throws Exception {
        // given
        long teammate = fixtures.saveMember("변경할 멤버");
        Runnable mutation = membershipChange(
                change,
                teammate
        );
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicLong holderPid = new AtomicLong();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Long> generation = executor.submit(() -> transactions.execute(status -> {
                long documentId = complete();
                holderPid.set(databasePid());
                locked.countDown();
                awaitSignal(release);
                return documentId;
            }));
            try {
                awaitSignal(locked);
                Future<?> membership = executor.submit(mutation);
                // when
                awaitDatabaseWaiter(holderPid.get());
                release.countDown();
                long documentId = generation.get(
                        10,
                        TimeUnit.SECONDS
                );
                membership.get(
                        10,
                        TimeUnit.SECONDS
                );
                // then
                if (change.equals("join")) {
                    assertThat(targets(documentId)).containsExactly(memberId);
                    assertThat(
                            details.find(
                                    workspaceId,
                                    teammate,
                                    documentId
                            )
                                    .myConfirmationState()
                    ).isEqualTo(MyConfirmationState.NOT_REQUIRED);
                } else {
                    assertThat(targets(documentId)).containsExactly(
                            memberId,
                            teammate
                    );
                    assertThat(
                            details.find(
                                    workspaceId,
                                    memberId,
                                    documentId
                            )
                                    .confirmationSummary()
                                    .excludedCount()
                    ).isEqualTo(1);
                    assertThat(
                            confirmationCommands.confirm(
                                    workspaceId,
                                    memberId,
                                    documentId
                            )
                                    .documentStatus()
                    ).isEqualTo(DocumentStatus.ARCHIVED);
                }
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    @DisplayName("일부 주제 성공은 다른 주제 실패와 관계없이 목록·상세·확인에 공개된다")
    void completeGeneration_success_partialRecordingResults() {
        // given
        long sibling = transactions.execute(status -> {
            DocumentGenerationJob current = jobs.findByWorkspaceIdAndIdForUpdate(
                    workspaceId,
                    jobId
            )
                    .orElseThrow();
            jdbc.sql("INSERT INTO document_generation_batch_topics (batch_id, position, topic) VALUES (:id, 1, '알림')")
                    .param(
                            "id",
                            current.getBatchId()
                    )
                    .update();
            DocumentGenerationJob other = jobs.save(
                    DocumentGenerationJob.queueGeneration(
                            current.getBatchId(),
                            transcriptId,
                            "알림",
                            NOW
                    )
            );
            other.startRunning(NOW);
            other.recordFailure(NOW);
            return other.getId();
        });
        // when
        long documentId = results.completeGeneration(
                workspaceId,
                jobId,
                1,
                new DocumentGenerationResult(
                        RESULT.title(),
                        null,
                        RESULT.content()
                )
        );
        // then
        assertThat(
                lists.find(
                        workspaceId,
                        memberId,
                        DocumentListParameters.of(
                                null,
                                null,
                                null,
                                recordingId
                        )
                )
                        .items()
        ).extracting(DocumentCardResult::id)
                .containsExactly(documentId);
        assertThat(
                details.find(
                        workspaceId,
                        memberId,
                        documentId
                )
                        .summary()
        ).isNull();
        assertThat(
                confirmationCommands.confirm(
                        workspaceId,
                        memberId,
                        documentId
                )
                        .documentStatus()
        ).isEqualTo(DocumentStatus.ARCHIVED);
        assertThat(
                jdbc.sql("SELECT status FROM document_generation_jobs WHERE id = :id")
                        .param(
                                "id",
                                sibling
                        )
                        .query(String.class)
                        .single()
        ).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("마지막 멤버가 탈퇴해 삭제된 Workspace에는 문서를 저장하지 않는다")
    void completeGeneration_failure_deletedWorkspace() {
        // given
        departures.leave(
                memberId,
                workspaceId
        );
        // when & then
        assertThatThrownBy(this::complete).isInstanceOf(WorkspaceException.class)
                .hasMessage(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED.getMessage());
        assertNoSavedDocument();
    }

    @Test
    @DisplayName("없는 Job 또는 다른 Workspace의 Job은 범위를 넘어 저장할 수 없다")
    void completeGeneration_failure_jobScope() {
        // given
        long otherWorkspace = fixtures.saveWorkspace();
        // when & then
        assertThatThrownBy(
                () -> results.completeGeneration(
                        workspaceId,
                        Long.MAX_VALUE,
                        1,
                        RESULT
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.DOCUMENT_GENERATION_JOB_NOT_FOUND.getMessage());
        assertThatThrownBy(
                () -> results.completeGeneration(
                        otherWorkspace,
                        jobId,
                        1,
                        RESULT
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.DOCUMENT_GENERATION_JOB_NOT_FOUND.getMessage());
        assertNoSavedDocument();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("잘못된 Workspace 또는 Job 식별자를 거절한다")
    void completeGeneration_failure_invalidIdentifier(long invalid) {
        // when & then
        assertThatThrownBy(
                () -> results.completeGeneration(
                        invalid,
                        jobId,
                        1,
                        RESULT
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.INVALID_PARAMETER.getMessage());
        assertThatThrownBy(
                () -> results.completeGeneration(
                        workspaceId,
                        invalid,
                        1,
                        RESULT
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.INVALID_PARAMETER.getMessage());
        assertNoSavedDocument();
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "FAILED"})
    @DisplayName("현재 시도라도 실행 중이 아니면 성공 결과를 반영하지 않는다")
    void completeGeneration_failure_notRunning(String jobState) {
        // given
        transactions.executeWithoutResult(status -> {
            DocumentGenerationJob job = jobs.findByWorkspaceIdAndIdForUpdate(
                    workspaceId,
                    jobId
            )
                    .orElseThrow();
            job.recordFailure(NOW);
            if (jobState.equals("QUEUED")) {
                job.retryByUser(NOW);
            }
        });
        int attempt = jdbc.sql("SELECT attempt_count FROM document_generation_jobs WHERE id = :id")
                .param(
                        "id",
                        jobId
                )
                .query(Integer.class)
                .single();
        // when & then
        assertThatThrownBy(
                () -> results.completeGeneration(
                        workspaceId,
                        jobId,
                        attempt,
                        RESULT
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT.getMessage());
        assertThat(
                jdbc.sql("SELECT count(*) FROM documents")
                        .query(Long.class)
                        .single()
        ).isZero();
        assertThat(jobStatus()).isEqualTo(jobState);
    }

    @Test
    @DisplayName("이미 성공한 분류 Job의 결과를 문서로 저장하지 않는다")
    void completeGeneration_failure_classificationJob() {
        // given
        long classifier = jdbc.sql("SELECT id FROM document_generation_jobs WHERE stage = 'CLASSIFICATION'")
                .query(Long.class)
                .single();
        // when & then
        assertThatThrownBy(
                () -> results.completeGeneration(
                        workspaceId,
                        classifier,
                        1,
                        RESULT
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT.getMessage());
        assertNoSavedDocument();
    }

    @Test
    @DisplayName("입력 조회가 원문 없음으로 끝나면 문서와 확인 대상을 만들지 않는다")
    void completeGeneration_failure_missingInput() {
        // given
        doReturn(Optional.empty()).when(inputs)
                .findForUpdate(
                        workspaceId,
                        transcriptId
                );
        // when & then
        assertThatThrownBy(this::complete).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.TRANSCRIPT_NOT_FOUND.getMessage());
        assertNoSavedDocument();
    }

    @Test
    @DisplayName("사용할 수 없는 빈 저장 원문은 생성 결과를 접수하지 않는다")
    void completeGeneration_failure_blankStoredTranscript() {
        // given
        jdbc.sql("UPDATE transcripts SET content = :text WHERE id = :id")
                .param(
                        "text",
                        "\u00a0\u3000"
                )
                .param(
                        "id",
                        transcriptId
                )
                .update();
        // when & then
        assertThatThrownBy(this::complete).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_INPUT.getMessage());
        assertNoSavedDocument();
    }

    @Test
    @DisplayName("주제 등록 완료가 아닌 Batch에서는 생성 결과를 저장하지 않는다")
    void completeGeneration_failure_unregisteredBatch() {
        // given
        jdbc.sql(
                "UPDATE document_generation_batches SET topic_registration_state = 'WAITING_CLASSIFICATION', registered_at = NULL"
        )
                .update();
        // when & then
        assertThatThrownBy(this::complete).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT.getMessage());
        assertNoSavedDocument();
    }

    @Test
    @DisplayName("활성 확인 대상이 없는 불일치 Workspace에서는 DRAFT를 만들지 않는다")
    void completeGeneration_failure_noActiveTargets() {
        // given
        fixtures.leave(
                workspaceId,
                memberId
        );
        // when & then
        assertThatThrownBy(this::complete).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT.getMessage());
        assertNoSavedDocument();
    }

    @Test
    @DisplayName("성공 시각이 실행 시작 이전이면 문서 삽입도 롤백한다")
    void completeGeneration_failure_timeBeforeStart() {
        // given
        when(clock.instant()).thenReturn(NOW.minusSeconds(1));
        // when & then
        assertThatThrownBy(this::complete).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.INVALID_DOCUMENT_DATA.getMessage());
        assertNoSavedDocument();
    }

    @Test
    @DisplayName("DB는 성공 문서의 원문 삭제와 같은 Job의 중복 문서를 거절한다")
    void completeGeneration_success_preservedSourceAndUniqueness() {
        // given
        complete();
        // when & then
        assertThatThrownBy(
                () -> jdbc.sql("DELETE FROM transcripts WHERE id = :id")
                        .param(
                                "id",
                                transcriptId
                        )
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                () -> jdbc.sql("""
                        INSERT INTO documents (workspace_id, recording_session_id, source_transcript_id,
                            document_generation_job_id, topic, title, content, status, created_at)
                        SELECT workspace_id, recording_session_id, source_transcript_id, document_generation_job_id,
                            '다른 주제', title, content, status, created_at FROM documents
                        """)
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(
                jdbc.sql("SELECT count(*) FROM documents")
                        .query(Long.class)
                        .single()
        ).isEqualTo(1);
        assertThat(jobStatus()).isEqualTo("SUCCEEDED");
    }

    private Runnable membershipChange(
            String change,
            long teammate
    ) {
        if (change.equals("join")) {
            String code = invitations.issue(
                    workspaceId,
                    memberId
            )
                    .code();
            return () -> acceptance.accept(
                    code,
                    "127.0.0.1",
                    teammate
            );
        }
        fixtures.join(
                workspaceId,
                teammate
        );
        return () -> departures.leave(
                teammate,
                workspaceId
        );
    }

    private long databasePid() {
        return jdbc.sql("SELECT pg_backend_pid()")
                .query(Long.class)
                .single();
    }

    private void awaitSignal(CountDownLatch signal) {
        try {
            if (!signal.await(
                    10,
                    TimeUnit.SECONDS
            )) {
                throw new AssertionError("트랜잭션 진행 신호를 받지 못했습니다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
            throw new AssertionError(exception);
        }
    }

    private void awaitDatabaseWaiter(long holderPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (jdbc.sql("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE :pid = ANY(pg_blocking_pids(pid)))")
                    .param(
                            "pid",
                            holderPid
                    )
                    .query(Boolean.class)
                    .single()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Workspace의 실제 PostgreSQL 잠금 대기를 확인하지 못했습니다");
    }

    private long complete() {
        return results.completeGeneration(
                workspaceId,
                jobId,
                1,
                RESULT
        );
    }

    private void startJob(long id) {
        transactions.executeWithoutResult(
                status -> jobs.findByWorkspaceIdAndIdForUpdate(
                        workspaceId,
                        id
                )
                        .orElseThrow()
                        .startRunning(NOW)
        );
    }

    private List<Long> targets(long documentId) {
        return jdbc.sql("SELECT member_id FROM document_confirmations WHERE document_id = :id ORDER BY member_id")
                .param(
                        "id",
                        documentId
                )
                .query(Long.class)
                .list();
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

    private void assertNoSavedDocument() {
        assertThat(
                jdbc.sql("SELECT count(*) FROM documents")
                        .query(Long.class)
                        .single()
        ).isZero();
        assertThat(
                jdbc.sql("SELECT count(*) FROM document_confirmations")
                        .query(Long.class)
                        .single()
        ).isZero();
        assertThat(jobStatus()).isEqualTo("RUNNING");
    }
}
