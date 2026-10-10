package com.knot.backend.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.testsupport.TestcontainersConfiguration;
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
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class DocumentSchemaIntegrationTest {
    @Autowired
    private JdbcClient jdbc;
    private DocumentFixtures fixtures;
    private long memberId;
    private long workspaceId;
    private long recordingId;
    private long transcriptId;
    private long jobId;

    @BeforeEach
    void setUp() {
        fixtures = new DocumentFixtures(jdbc);
        memberId = fixtures.saveMember("문서 작성자");
        workspaceId = fixtures.saveWorkspace();
        recordingId = fixtures.saveRecording(
                workspaceId,
                memberId,
                1850999
        );
        transcriptId = fixtures.saveTranscript(recordingId);
        jobId = fixtures.saveJob(
                transcriptId,
                "SUCCEEDED"
        );
    }

    @Test
    @DisplayName("문서와 고정 확인 대상을 저장한다")
    void save_success() {
        // given
        long documentId = fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                jobId,
                "운영 정책"
        );

        // when
        fixtures.target(
                documentId,
                memberId,
                null
        );

        // then
        assertThat(
                jdbc.sql("SELECT count(*) FROM document_confirmations WHERE document_id = :id")
                        .param(
                                "id",
                                documentId
                        )
                        .query(Long.class)
                        .single()
        ).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 녹음의 같은 주제는 다른 Job이어도 중복 저장하지 못한다")
    void save_failure_duplicateRecordingTopic() {
        // given
        fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                jobId,
                "운영 정책"
        );
        long otherJobId = fixtures.saveJob(
                transcriptId,
                "SUCCEEDED"
        );

        // when & then
        assertThatThrownBy(
                () -> fixtures.saveDocument(
                        workspaceId,
                        recordingId,
                        transcriptId,
                        otherJobId,
                        "운영 정책"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 Job은 다른 주제의 문서도 중복 저장하지 못한다")
    void save_failure_duplicateJob() {
        // given
        fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                jobId,
                "운영 정책"
        );

        // when & then
        assertThatThrownBy(
                () -> fixtures.saveDocument(
                        workspaceId,
                        recordingId,
                        transcriptId,
                        jobId,
                        "개발 정책"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 녹음은 같은 주제의 문서를 저장할 수 있다")
    void save_success_sameTopicDifferentRecording() {
        // given
        fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                jobId,
                "운영 정책"
        );
        long otherRecording = fixtures.saveRecording(
                workspaceId,
                memberId,
                1000
        );
        long otherTranscript = fixtures.saveTranscript(otherRecording);
        long otherJob = fixtures.saveJob(
                otherTranscript,
                "SUCCEEDED"
        );

        // when
        long documentId = fixtures.saveDocument(
                workspaceId,
                otherRecording,
                otherTranscript,
                otherJob,
                "운영 정책"
        );

        // then
        assertThat(documentId).isPositive();
    }

    @Test
    @DisplayName("다른 Workspace를 문서 출처에 연결하지 못한다")
    void save_failure_wrongWorkspaceSource() {
        // given
        long otherWorkspace = fixtures.saveWorkspace();

        // when & then
        assertThatThrownBy(
                () -> fixtures.saveDocument(
                        otherWorkspace,
                        recordingId,
                        transcriptId,
                        jobId,
                        "운영 정책"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 녹음 원문을 문서에 연결하지 못한다")
    void save_failure_wrongTranscriptSource() {
        // given
        long otherRecording = fixtures.saveRecording(
                workspaceId,
                memberId,
                1000
        );
        long otherTranscript = fixtures.saveTranscript(otherRecording);
        long otherJob = fixtures.saveJob(
                otherTranscript,
                "SUCCEEDED"
        );

        // when & then
        assertThatThrownBy(
                () -> fixtures.saveDocument(
                        workspaceId,
                        recordingId,
                        otherTranscript,
                        otherJob,
                        "운영 정책"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 입력 원문의 Job을 문서에 연결하지 못한다")
    void save_failure_wrongJobSource() {
        // given
        long otherRecording = fixtures.saveRecording(
                workspaceId,
                memberId,
                1000
        );
        long otherTranscript = fixtures.saveTranscript(otherRecording);
        long otherJob = fixtures.saveJob(
                otherTranscript,
                "SUCCEEDED"
        );

        // when & then
        assertThatThrownBy(
                () -> fixtures.saveDocument(
                        workspaceId,
                        recordingId,
                        transcriptId,
                        otherJob,
                        "운영 정책"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("성공 문서가 참조하는 Job을 삭제하지 못한다")
    void delete_failure_referencedJob() {
        // given
        fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                jobId,
                "운영 정책"
        );

        // when & then
        assertThatThrownBy(
                () -> jdbc.sql("DELETE FROM document_generation_jobs WHERE id = :id")
                        .param(
                                "id",
                                jobId
                        )
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("성공 문서의 연결 원문을 삭제하지 못한다")
    void delete_failure_referencedTranscript() {
        // given
        fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                jobId,
                "운영 정책"
        );

        // when & then
        assertThatThrownBy(
                () -> jdbc.sql("DELETE FROM transcripts WHERE id = :id")
                        .param(
                                "id",
                                transcriptId
                        )
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 문서의 같은 확인 대상을 중복 저장하지 못한다")
    void save_failure_duplicateConfirmation() {
        // given
        long documentId = fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                jobId,
                "운영 정책"
        );
        fixtures.target(
                documentId,
                memberId,
                null
        );

        // when & then
        assertThatThrownBy(
                () -> fixtures.target(
                        documentId,
                        memberId,
                        null
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("보관 시각이 없는 ARCHIVED 문서는 저장하지 못한다")
    void save_failure_archivedWithoutTimestamp() {
        // given
        long documentId = fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                jobId,
                "운영 정책"
        );

        // when & then
        assertThatThrownBy(
                () -> jdbc.sql("UPDATE documents SET status = 'ARCHIVED' WHERE id = :id")
                        .param(
                                "id",
                                documentId
                        )
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
