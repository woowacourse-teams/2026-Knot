package com.knot.backend.recording.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TranscriptSegmentTest {

    @Test
    @DisplayName("시작 시각 0과 미상 종료 시각·화자로 구간을 생성한다")
    void create_success_unknownEndAndSpeaker() {
        // when
        TranscriptSegment segment = TranscriptSegment.create(
                81,
                0,
                0,
                null,
                null,
                "발언"
        );

        // then
        assertThat(segment.getTranscriptId()).isEqualTo(81);
        assertThat(segment.getPosition()).isZero();
        assertThat(segment.getStartMillis()).isZero();
        assertThat(segment.getEndMillis()).isNull();
        assertThat(segment.getSpeakerNumber()).isNull();
        assertThat(segment.getText()).isEqualTo("발언");
    }

    @Test
    @DisplayName("같은 시작·종료 시각과 익명 화자 번호를 유지한다")
    void create_success_knownEndAndSpeaker() {
        // when
        TranscriptSegment segment = TranscriptSegment.create(
                81,
                1,
                1200,
                1200L,
                2,
                "발언"
        );

        // then
        assertThat(segment.getEndMillis()).isEqualTo(1200);
        assertThat(segment.getSpeakerNumber()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"0,0,0", "81,-1,0", "81,0,-1"})
    @DisplayName("잘못된 원문 식별자·순서·시작 시각을 거절한다")
    void create_failure_invalidRequiredValues(
            long transcriptId,
            int position,
            long startMillis
    ) {
        // when & then
        assertThatThrownBy(
                () -> TranscriptSegment.create(
                        transcriptId,
                        position,
                        startMillis,
                        null,
                        null,
                        "발언"
                )
        ).isInstanceOf(RecordingException.class);
    }

    @Test
    @DisplayName("시작보다 이른 종료 시각을 거절한다")
    void create_failure_reversedTime() {
        // when & then
        assertThatThrownBy(
                () -> TranscriptSegment.create(
                        81,
                        0,
                        100,
                        99L,
                        null,
                        "발언"
                )
        ).isInstanceOf(RecordingException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("화자 번호는 1 이상이어야 한다")
    void create_failure_invalidSpeaker(int speakerNumber) {
        // when & then
        assertThatThrownBy(
                () -> TranscriptSegment.create(
                        81,
                        0,
                        0,
                        null,
                        speakerNumber,
                        "발언"
                )
        ).isInstanceOf(RecordingException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    @DisplayName("문장이 없거나 공백만 있으면 거절한다")
    void create_failure_blankText(String text) {
        // when & then
        assertThatThrownBy(
                () -> TranscriptSegment.create(
                        81,
                        0,
                        0,
                        null,
                        null,
                        text
                )
        ).isInstanceOf(RecordingException.class);
    }
}
