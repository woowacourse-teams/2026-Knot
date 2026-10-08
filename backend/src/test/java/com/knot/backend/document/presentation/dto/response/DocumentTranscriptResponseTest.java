package com.knot.backend.document.presentation.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.application.dto.result.DocumentTranscriptResult;
import com.knot.backend.document.application.dto.result.DocumentTranscriptSnapshot;
import com.knot.backend.recording.application.dto.result.TranscriptSegmentResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentTranscriptResponseTest {

    @Test
    @DisplayName("응답은 전체 텍스트와 실제 밀리초 및 미상 종료 시각·화자를 유지한다")
    void from_success_preservesTranscriptContract() {
        // given
        DocumentTranscriptSnapshot snapshot = new DocumentTranscriptSnapshot(
                81,
                1850999,
                "전체 원문",
                List.of(
                        new TranscriptSegmentResult(
                                0,
                                null,
                                null,
                                "첫 발언"
                        ),
                        new TranscriptSegmentResult(
                                1200,
                                4600L,
                                1,
                                "다음 발언"
                        )
                )
        );

        // when
        DocumentTranscriptResponse response = DocumentTranscriptResponse.from(DocumentTranscriptResult.from(snapshot));

        // then
        assertThat(response.transcriptId()).isEqualTo(81);
        assertThat(response.isPartial()).isFalse();
        assertThat(response.recordingDurationSeconds()).isEqualTo(1850);
        assertThat(response.transcriptText()).isEqualTo("전체 원문");
        assertThat(response.segments()).hasSize(2);
        assertThat(
                response.segments()
                        .getFirst()
                        .startMillis()
        ).isZero();
        assertThat(
                response.segments()
                        .getFirst()
                        .endMillis()
        ).isNull();
        assertThat(
                response.segments()
                        .getFirst()
                        .speakerNumber()
        ).isNull();
        assertThat(
                response.segments()
                        .get(1)
                        .endMillis()
        ).isEqualTo(4600);
        assertThat(
                response.segments()
                        .get(1)
                        .speakerNumber()
        ).isEqualTo(1);
    }

    @Test
    @DisplayName("변환한 구간 목록은 원본 목록 변경으로 달라지지 않는다")
    void from_success_immutableSegments() {
        // given
        List<TranscriptSegmentResult> source = new ArrayList<>();
        source.add(
                new TranscriptSegmentResult(
                        0,
                        null,
                        null,
                        "첫 발언"
                )
        );
        DocumentTranscriptResult result = DocumentTranscriptResult.from(
                new DocumentTranscriptSnapshot(
                        81,
                        999,
                        "전체 원문",
                        source
                )
        );

        // when
        DocumentTranscriptResponse response = DocumentTranscriptResponse.from(result);
        source.clear();

        // then
        assertThat(response.recordingDurationSeconds()).isZero();
        assertThat(response.segments()).hasSize(1);
        assertThatThrownBy(
                () -> response.segments()
                        .clear()
        ).isInstanceOf(UnsupportedOperationException.class);
    }
}
