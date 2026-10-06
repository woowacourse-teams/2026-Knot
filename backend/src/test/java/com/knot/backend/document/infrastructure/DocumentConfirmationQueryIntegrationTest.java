package com.knot.backend.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.application.DocumentConfirmationQuery;
import com.knot.backend.document.application.dto.result.DocumentConfirmationOverviewResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
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
@Import({TestcontainersConfiguration.class, DocumentConfirmationQueryAdapter.class})
class DocumentConfirmationQueryIntegrationTest {
    @Autowired
    private DocumentConfirmationQuery query;
    @Autowired
    private JdbcClient jdbc;
    private DocumentFixtures fixtures;
    private long workspaceId;
    private long viewerId;
    private long documentId;

    @BeforeEach
    void setUp() {
        fixtures = new DocumentFixtures(jdbc);
        workspaceId = fixtures.saveWorkspace();
        viewerId = fixtures.saveMember("조회자");
        fixtures.join(
                workspaceId,
                viewerId
        );
        long recordingId = fixtures.saveRecording(
                workspaceId,
                viewerId,
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
    }

    @Test
    @DisplayName("문서가 있고 대상이 없으면 0명 집계와 빈 페이지를 반환한다")
    void findSummary_success_emptyTargets() {
        // when
        DocumentConfirmationOverviewResult result = summary(viewerId);
        // then
        assertThat(result.documentId()).isEqualTo(documentId);
        assertThat(result.summary()).isEqualTo(
                new DocumentConfirmationSummaryResult(
                        0,
                        0,
                        0
                )
        );
        assertThat(result.confirmedByMe()).isFalse();

    }

    @Test
    @DisplayName("전체 집계는 페이지 크기와 무관하며 확인 후 탈퇴한 대상도 확인 인원에 포함한다")
    void findSummary_success_allStatesAndConfirmedViewer() {
        // given
        fixtures.target(
                documentId,
                viewerId,
                DocumentFixtures.CREATED_AT
        );
        long departedConfirmed = target(
                false,
                DocumentFixtures.CREATED_AT
        );
        long pending = target(
                true,
                null
        );
        long excluded = target(
                false,
                null
        );
        // when
        DocumentConfirmationOverviewResult result = summary(viewerId);
        // then
        assertThat(result.confirmedByMe()).isTrue();
        assertThat(result.summary()).isEqualTo(
                new DocumentConfirmationSummaryResult(
                        2,
                        1,
                        1
                )
        );

    }

    @Test
    @DisplayName("현재 가입했어도 생성 당시 대상이 아니면 목록에서 제외하고 내 확인 여부는 false다")
    void findSummary_success_laterJoinerNotRequired() {
        // given
        target(
                true,
                DocumentFixtures.CREATED_AT
        );
        // when & then
        assertThat(summary(viewerId).confirmedByMe()).isFalse();

    }

    @Test
    @DisplayName("없는 문서와 다른 Workspace 문서는 대상 0명인 문서와 구분한다")
    void findSummary_failure_missingOrForeignDocument() {
        // given
        fixtures.target(
                documentId,
                viewerId,
                null
        );
        long otherWorkspace = fixtures.saveWorkspace();
        // when & then
        assertThat(
                query.findSummary(
                        workspaceId,
                        Long.MAX_VALUE,
                        viewerId
                )
        ).isEmpty();
        assertThat(
                query.findSummary(
                        otherWorkspace,
                        documentId,
                        viewerId
                )
        ).isEmpty();

    }

    private DocumentConfirmationOverviewResult summary(long memberId) {
        return query.findSummary(
                workspaceId,
                documentId,
                memberId
        )
                .orElseThrow();
    }

    private long target(
            boolean active,
            Instant confirmedAt
    ) {
        long memberId = fixtures.saveMember("대상" + System.nanoTime());
        fixtures.join(
                workspaceId,
                memberId
        );
        fixtures.target(
                documentId,
                memberId,
                confirmedAt
        );
        if (!active) {
            fixtures.leave(
                    workspaceId,
                    memberId
            );
        }
        return memberId;
    }
}
