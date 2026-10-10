package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentGenerationTargetTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    @DisplayName("Batch가 등록된 주제·녹음·원문의 생성 대상 일치를 검증한다")
    void validateGenerationTarget_success() {
        // given
        DocumentGenerationBatch batch = registeredBatch();
        DocumentGenerationJob job = job(
                3,
                "검색"
        );

        // when & then
        assertThatCode(
                () -> batch.validateGenerationTarget(
                        job,
                        2,
                        3
                )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("미등록·다른 녹음·다른 원문·미등록 주제를 거절한다")
    void validateGenerationTarget_failure() {
        // given
        DocumentGenerationBatch batch = registeredBatch();
        DocumentGenerationJob job = job(
                3,
                "검색"
        );
        DocumentGenerationBatch waiting = DocumentGenerationBatch.accept(
                2,
                3,
                NOW
        );

        // when & then
        assertThatThrownBy(
                () -> waiting.validateGenerationTarget(
                        job,
                        2,
                        3
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> batch.validateGenerationTarget(
                        job,
                        99,
                        3
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> batch.validateGenerationTarget(
                        job,
                        2,
                        99
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> batch.validateGenerationTarget(
                        job(
                                3,
                                "알림"
                        ),
                        2,
                        3
                )
        ).hasMessage(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT.getMessage());
    }

    @Test
    @DisplayName("Document가 완료된 Job의 Workspace·원문·주제를 확인한다")
    void validateGeneratedBy_success() {
        // given
        Document document = document();

        // when & then
        assertThatCode(
                () -> document.validateGeneratedBy(
                        job(
                                3,
                                "검색"
                        ),
                        1
                )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("완료 문서와 다른 Workspace·원문·주제는 충돌로 처리한다")
    void validateGeneratedBy_failure() {
        // given
        Document document = document();

        // when & then
        assertThatThrownBy(
                () -> document.validateGeneratedBy(
                        job(
                                3,
                                "검색"
                        ),
                        99
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> document.validateGeneratedBy(
                        job(
                                99,
                                "검색"
                        ),
                        1
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> document.validateGeneratedBy(
                        job(
                                3,
                                "알림"
                        ),
                        1
                )
        ).hasMessage(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT.getMessage());
    }

    private DocumentGenerationBatch registeredBatch() {
        DocumentGenerationBatch batch = DocumentGenerationBatch.accept(
                2,
                3,
                NOW
        );
        batch.registerTopics(
                List.of(DocumentTopic.of("검색")),
                NOW
        );
        return batch;
    }

    private DocumentGenerationJob job(
            long transcriptId,
            String topic
    ) {
        return DocumentGenerationJob.queueGeneration(
                1,
                transcriptId,
                DocumentTopic.of(topic),
                NOW
        );
    }

    private Document document() {
        return Document.createDraft(
                1,
                2,
                3,
                4,
                "검색",
                "제목",
                null,
                "본문",
                NOW
        );
    }
}
