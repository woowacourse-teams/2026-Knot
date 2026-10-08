package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.application.dto.result.DocumentTopicClassificationResult;
import com.knot.backend.document.domain.DocumentConfirmationRepository;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentRepository;
import com.knot.backend.document.domain.DocumentStatus;
import com.knot.backend.document.domain.MyConfirmationState;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
    private TransactionTemplate transactions;
    @Autowired
    private JdbcClient jdbc;
    @MockitoSpyBean
    private DocumentRepository documents;
    @MockitoSpyBean
    private DocumentConfirmationRepository confirmations;
    @MockitoSpyBean
    private DocumentGenerationJobRepository jobs;
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
