package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-06T00:00:00Z");

    @Test
    @DisplayName("보관하면 ARCHIVED와 전환 시각을 저장하며 본문은 유지한다")
    void archive_success() {
        // given
        Document document = draft();
        Instant archivedAt = CREATED_AT.plusSeconds(60);

        // when
        document.archive(archivedAt);

        // then
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ARCHIVED);
        assertThat(document.getArchivedAt()).isEqualTo(archivedAt);
        assertThat(document.getContent()).isEqualTo("본문");
    }

    @Test
    @DisplayName("반복 보관은 최초 전환 시각을 유지한다")
    void archive_success_repeatedRequest() {
        // given
        Document document = draft();
        Instant archivedAt = CREATED_AT.plusSeconds(60);
        document.archive(archivedAt);

        // when
        document.archive(archivedAt.plusSeconds(60));

        // then
        assertThat(document.getArchivedAt()).isEqualTo(archivedAt);
    }

    @Test
    @DisplayName("생성 시각과 같은 시각에 보관할 수 있다")
    void archive_success_creationBoundary() {
        // given
        Document document = draft();

        // when
        document.archive(CREATED_AT);

        // then
        assertThat(document.getArchivedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("생성 이전 보관 시각은 거절하고 DRAFT를 유지한다")
    void archive_failure_beforeCreation() {
        // given
        Document document = draft();

        // when & then
        assertThatThrownBy(() -> document.archive(CREATED_AT.minusNanos(1))).isInstanceOf(DocumentException.class);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(document.getArchivedAt()).isNull();
    }

    @Test
    @DisplayName("보관 시각이 없으면 거절한다")
    void archive_failure_missingTime() {
        // given
        Document document = draft();

        // when & then
        assertThatThrownBy(() -> document.archive(null)).isInstanceOf(DocumentException.class);
    }

    private Document draft() {
        return Document.createDraft(
                1,
                2,
                3,
                4,
                "정책",
                "제목",
                null,
                "본문",
                CREATED_AT
        );
    }

    @Test
    @DisplayName("문서는 nullable 요약과 DRAFT 상태로 생성된다")
    void createDraft_success() {
        // when
        Document document = Document.createDraft(
                1,
                2,
                3,
                4,
                "운영 정책",
                "제목",
                null,
                "본문",
                CREATED_AT
        );

        // then
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(document.getSummary()).isNull();
        assertThat(document.getArchivedAt()).isNull();
        assertThat(document.getSourceTranscriptId()).isEqualTo(3);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    @DisplayName("문서의 빈 제목은 거절한다")
    void createDraft_failure_blankTitle(String title) {
        // when & then
        assertThatThrownBy(
                () -> Document.createDraft(
                        1,
                        2,
                        3,
                        4,
                        "운영 정책",
                        title,
                        null,
                        "본문",
                        CREATED_AT
                )
        ).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("문서의 잘못된 출처 ID는 거절한다")
    void createDraft_failure_invalidSource() {
        // when & then
        assertThatThrownBy(
                () -> Document.createDraft(
                        1,
                        2,
                        0,
                        4,
                        "운영 정책",
                        "제목",
                        null,
                        "본문",
                        CREATED_AT
                )
        ).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("문서 생성 시각이 없으면 거절한다")
    void createDraft_failure_missingTime() {
        // when & then
        assertThatThrownBy(
                () -> Document.createDraft(
                        1,
                        2,
                        3,
                        4,
                        "운영 정책",
                        "제목",
                        null,
                        "본문",
                        null
                )
        ).isInstanceOf(DocumentException.class);
    }
}
