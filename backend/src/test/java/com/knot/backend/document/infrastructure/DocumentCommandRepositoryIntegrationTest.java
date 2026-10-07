package com.knot.backend.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.domain.Document;
import com.knot.backend.document.domain.DocumentConfirmation;
import com.knot.backend.document.domain.DocumentConfirmationRepository;
import com.knot.backend.document.domain.DocumentRepository;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@DataJpaTest
@Import({TestcontainersConfiguration.class, DocumentRepositoryAdapter.class,
        DocumentConfirmationRepositoryAdapter.class})
class DocumentCommandRepositoryIntegrationTest {
    @Autowired
    private DocumentRepository documents;
    @Autowired
    private DocumentConfirmationRepository confirmations;
    @Autowired
    private JdbcClient jdbc;
    private long workspaceId;
    private long memberId;
    private long documentId;

    @BeforeEach
    void setUp() {
        DocumentFixtures fixtures = new DocumentFixtures(jdbc);
        memberId = fixtures.saveMember("대상");
        workspaceId = fixtures.saveWorkspace();
        fixtures.join(
                workspaceId,
                memberId
        );
        long recordingId = fixtures.saveRecording(
                workspaceId,
                memberId,
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
                memberId,
                null
        );
    }

    @Test
    @DisplayName("문서 잠금 조회는 Workspace 범위 안에서만 반환한다")
    void findByWorkspaceIdAndIdForUpdate_success() {
        // when
        Document document = documents.findByWorkspaceIdAndIdForUpdate(
                workspaceId,
                documentId
        )
                .orElseThrow();

        // then
        assertThat(document.getId()).isEqualTo(documentId);
        assertThat(
                documents.findByWorkspaceIdAndIdForUpdate(
                        workspaceId + 100,
                        documentId
                )
        ).isEmpty();
        assertThat(
                documents.findByWorkspaceIdAndIdForUpdate(
                        workspaceId,
                        Long.MAX_VALUE
                )
        ).isEmpty();
    }

    @Test
    @DisplayName("기존 확인 대상만 반환하며 없는 대상을 만들지 않는다")
    void findByDocumentIdAndMemberId_success() {
        // when
        DocumentConfirmation confirmation = confirmations.findByDocumentIdAndMemberId(
                documentId,
                memberId
        )
                .orElseThrow();

        // then
        assertThat(confirmation.getConfirmedAt()).isNull();
        assertThat(
                confirmations.findByDocumentIdAndMemberId(
                        documentId,
                        memberId + 100
                )
        ).isEmpty();
    }

    @Test
    @DisplayName("확인 변경을 flush하면 SQL 집계에도 저장된 시각이 보인다")
    void flush_success_confirmation() {
        // given
        Instant confirmedAt = DocumentFixtures.CREATED_AT.plusSeconds(60);
        DocumentConfirmation confirmation = confirmations.findByDocumentIdAndMemberId(
                documentId,
                memberId
        )
                .orElseThrow();

        // when
        confirmation.confirm(confirmedAt);
        confirmations.flush();

        // then
        assertThat(
                jdbc.sql("SELECT confirmed_at FROM document_confirmations WHERE document_id = :id")
                        .param(
                                "id",
                                documentId
                        )
                        .query(Instant.class)
                        .single()
        ).isEqualTo(confirmedAt);
    }

    @Test
    @DisplayName("미확인 대상의 DRAFT 문서만 보관 검토 대상으로 잠근다")
    void findAffectedDraftsForUpdate_success() {
        // when & then
        assertThat(
                documents.findAffectedDraftsForUpdate(
                        workspaceId,
                        memberId
                )
        ).extracting(Document::getId)
                .containsExactly(documentId);
        assertThat(
                documents.findAffectedDraftsForUpdate(
                        workspaceId + 100,
                        memberId
                )
        ).isEmpty();
        assertThat(
                documents.findAffectedDraftsForUpdate(
                        workspaceId,
                        memberId + 100
                )
        ).isEmpty();
    }

    @Test
    @DisplayName("이미 확인한 대상과 ARCHIVED 문서는 탈퇴 보관 검토에서 제외한다")
    void findAffectedDraftsForUpdate_success_excludesCompletedDocuments() {
        // given
        DocumentConfirmation confirmation = confirmations.findByDocumentIdAndMemberId(
                documentId,
                memberId
        )
                .orElseThrow();
        confirmation.confirm(DocumentFixtures.CREATED_AT);
        confirmations.flush();

        // when & then
        assertThat(
                documents.findAffectedDraftsForUpdate(
                        workspaceId,
                        memberId
                )
        ).isEmpty();
    }

    @Test
    @DisplayName("보관 상태와 보관 시각을 함께 flush한다")
    void flush_success_archive() {
        // given
        Document document = documents.findByWorkspaceIdAndIdForUpdate(
                workspaceId,
                documentId
        )
                .orElseThrow();
        document.archive(DocumentFixtures.CREATED_AT.plusSeconds(60));

        // when
        documents.flush();

        // then
        assertThat(
                jdbc.sql("SELECT status FROM documents WHERE id = :id")
                        .param(
                                "id",
                                documentId
                        )
                        .query(String.class)
                        .single()
        ).isEqualTo("ARCHIVED");
        assertThat(
                documents.findAffectedDraftsForUpdate(
                        workspaceId,
                        memberId
                )
        ).isEmpty();
    }
}
