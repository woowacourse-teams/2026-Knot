package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.application.dto.result.DocumentTopicClassificationResult;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationFailureCause;
import com.knot.backend.document.domain.DocumentGenerationProcessingStatus;
import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.exception.LlmException;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import org.springframework.dao.DataAccessException;
import static org.mockito.ArgumentMatchers.eq;
import java.util.concurrent.Executors;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@ActiveProfiles("dev")
@SpringBootTest(properties = {"knot.llm.enabled=true", "knot.llm.base-url=http://localhost:1234",
        "knot.llm.api-token=test-token", "knot.llm.model=test-model", "knot.document-generation.worker.enabled=false"})
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DocumentGenerationExecutionIntegrationTest {

    private static final Instant NOW = DocumentFixtures.CREATED_AT.plusSeconds(120);
    private static final DocumentGenerationResult RESULT = new DocumentGenerationResult(
            "제목",
            "요약",
            "## 핵심 요약\n내용"
    );

    @Autowired
    private DocumentGenerationIntakeService intake;
    @Autowired
    private DocumentGenerationClaimService claims;
    @Autowired
    private DocumentGenerationFailureService failures;
    @Autowired
    private DocumentGenerationRecoveryService recovery;
    @Autowired
    private DocumentGenerationProgressService progress;

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DocumentGenerationJobRepository jobRepository;
    @MockitoBean
    private Clock clock;
    @MockitoBean
    private DocumentTopicClassificationService classifier;
    @MockitoBean
    private DocumentGenerationService generator;
    private DocumentFixtures fixtures;
    private long memberId;
    private long workspaceId;
    private long recordingId;
    private long classifierId;

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
        long transcriptId = fixtures.saveTranscript(recordingId);
        classifierId = transactions.execute(
                status -> intake.acceptCompletedTranscript(
                        workspaceId,
                        recordingId,
                        transcriptId
                )
        )
                .classificationJobId();
        when(classifier.classify(anyString())).thenReturn(
                new DocumentTopicClassificationResult(
                        List.of(
                                "A",
                                "B",
                                "C"
                        )
                )
        );
        when(
                generator.generate(
                        anyString(),
                        anyString()
                )
        ).thenReturn(RESULT);
    }

    @Test
    @DisplayName("동시에 같은 작업을 확보해도 현재 실행은 하나만 접수한다")
    void claim_success_singleConsumer() throws Exception {
        // given
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> call = () -> {
                barrier.await(
                        10,
                        TimeUnit.SECONDS
                );
                return claims.claim(
                        workspaceId,
                        classifierId
                )
                        .isPresent();
            };
            // when
            Future<Boolean> first = executor.submit(call);
            Future<Boolean> second = executor.submit(call);
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
                    true,
                    false
            );
        }
        assertThat(
                number(
                        "attempt_count",
                        classifierId
                )
        ).isEqualTo(1);
        assertThat(
                progress.findProgress(
                        workspaceId,
                        recordingId
                )
                        .orElseThrow()
                        .status()
        ).isEqualTo(DocumentGenerationProcessingStatus.RUNNING);
    }

    @Test
    @DisplayName("429는 5초 뒤 한 번만 자동 재시도하며 중복 실패 통지는 횟수를 늘리지 않는다")
    void failAttempt_success_boundedScheduledRetry() {
        // given
        claims.claim(
                workspaceId,
                classifierId
        )
                .orElseThrow();
        // when
        failures.failAttempt(
                workspaceId,
                classifierId,
                1,
                DocumentGenerationFailureCause.RATE_LIMITED
        );
        failures.failAttempt(
                workspaceId,
                classifierId,
                1,
                DocumentGenerationFailureCause.RATE_LIMITED
        );
        // then
        assertThat(
                number(
                        "attempt_count",
                        classifierId
                )
        ).isEqualTo(2);
        assertThat(
                number(
                        "automatic_retry_count",
                        classifierId
                )
        ).isEqualTo(1);
        assertThat(
                number(
                        "user_retry_count",
                        classifierId
                )
        ).isZero();
        assertThat(
                claims.claim(
                        workspaceId,
                        classifierId
                )
        ).isEmpty();
        when(clock.instant()).thenReturn(NOW.plusSeconds(5));
        claims.claim(
                workspaceId,
                classifierId
        )
                .orElseThrow();
        failures.failAttempt(
                workspaceId,
                classifierId,
                2,
                DocumentGenerationFailureCause.TIMEOUT
        );
        assertThat(status(classifierId)).isEqualTo("FAILED");
        assertThat(
                number(
                        "attempt_count",
                        classifierId
                )
        ).isEqualTo(2);
    }

    private List<Long> generationIds() {
        return jdbc.sql("SELECT id FROM document_generation_jobs WHERE stage = 'GENERATION' ORDER BY id")
                .query(Long.class)
                .list();
    }

    @Test
    @DisplayName("만료 전 회수와 성공 이후 실패 통지는 현재 결과를 바꾸지 않는다")
    void recoverExpired_failure_notExpiredOrAlreadySucceeded() {
        // given
        claims.claim(
                workspaceId,
                classifierId
        )
                .orElseThrow();
        // when
        recovery.recoverExpired(
                workspaceId,
                classifierId,
                1
        );
        // then
        assertThat(status(classifierId)).isEqualTo("RUNNING");
        assertThat(
                number(
                        "attempt_count",
                        classifierId
                )
        ).isEqualTo(1);
        failures.failAttempt(
                workspaceId,
                classifierId,
                0,
                DocumentGenerationFailureCause.TIMEOUT
        );
        assertThat(status(classifierId)).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("두 번째 중단은 최종 실패로 남기고 무한 회수하지 않는다")
    void recoverExpired_failure_exhaustedAutomaticLimit() {
        // given
        claims.claim(
                workspaceId,
                classifierId
        )
                .orElseThrow();
        when(clock.instant()).thenReturn(NOW.plusSeconds(150));
        recovery.recoverExpired(
                workspaceId,
                classifierId,
                1
        );
        when(clock.instant()).thenReturn(NOW.plusSeconds(155));
        claims.claim(
                workspaceId,
                classifierId
        )
                .orElseThrow();
        // when
        when(clock.instant()).thenReturn(NOW.plusSeconds(305));
        recovery.recoverExpired(
                workspaceId,
                classifierId,
                2
        );
        // then
        assertThat(status(classifierId)).isEqualTo("FAILED");
        assertThat(
                number(
                        "automatic_retry_count",
                        classifierId
                )
        ).isEqualTo(1);
        assertThat(
                progress.findProgress(
                        workspaceId,
                        recordingId
                )
                        .orElseThrow()
                        .status()
        ).isEqualTo(DocumentGenerationProcessingStatus.FAILED);
        assertThat(
                jobRepository.findExpiredCandidates(
                        NOW.plusSeconds(305),
                        20
                )
        ).isEmpty();
    }

    @Test
    @DisplayName("미래에 예약된 작업을 제외하고 실행 기한이 만료된 작업만 회수 후보로 조회한다")
    void findCandidates_success_dueTimesOnly() {
        // given
        assertThat(
                jobRepository.findReadyCandidates(
                        NOW,
                        1
                )
        ).hasSize(1);
        claims.claim(
                workspaceId,
                classifierId
        )
                .orElseThrow();
        // when & then
        assertThat(
                jobRepository.findReadyCandidates(
                        NOW,
                        1
                )
        ).isEmpty();
        assertThat(
                jobRepository.findExpiredCandidates(
                        NOW.plusSeconds(149),
                        1
                )
        ).isEmpty();
        assertThat(
                jobRepository.findExpiredCandidates(
                        NOW.plusSeconds(150),
                        1
                )
        ).hasSize(1);
        failures.failAttempt(
                workspaceId,
                classifierId,
                1,
                DocumentGenerationFailureCause.UNAVAILABLE
        );
        assertThat(
                jobRepository.findReadyCandidates(
                        NOW.plusSeconds(4),
                        1
                )
        ).isEmpty();
        assertThat(
                jobRepository.findReadyCandidates(
                        NOW.plusSeconds(5),
                        1
                )
        ).hasSize(1);
    }

    @Test
    @DisplayName("실행 도중 Workspace가 삭제되면 만료 회수 시 재접수하지 않는다")
    void recoverExpired_failure_deletedWorkspace() {
        // given
        claims.claim(
                workspaceId,
                classifierId
        )
                .orElseThrow();
        jdbc.sql("UPDATE workspaces SET deleted_at = :now")
                .param(
                        "now",
                        Timestamp.from(NOW)
                )
                .update();
        when(clock.instant()).thenReturn(NOW.plusSeconds(150));
        // when
        recovery.recoverExpired(
                workspaceId,
                classifierId,
                1
        );
        // then
        assertThat(status(classifierId)).isEqualTo("FAILED");
        assertThat(
                number(
                        "attempt_count",
                        classifierId
                )
        ).isEqualTo(1);
        verifyNoInteractions(
                classifier,
                generator
        );
    }

    private int number(
            String column,
            long jobId
    ) {
        return jdbc.sql("SELECT " + column + " FROM document_generation_jobs WHERE id = :id")
                .param(
                        "id",
                        jobId
                )
                .query(Integer.class)
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

    private long documentCount() {
        return jdbc.sql("SELECT count(*) FROM documents")
                .query(Long.class)
                .single();
    }
}
