package com.knot.backend.recording.application.dto.result;

import com.knot.backend.recording.domain.TranscriptSegment;

public record TranscriptSegmentResult(
        long startMillis,
        Long endMillis,
        Integer speakerNumber,
        String text
) {

    public static TranscriptSegmentResult from(TranscriptSegment segment) {
        return new TranscriptSegmentResult(
                segment.getStartMillis(),
                segment.getEndMillis(),
                segment.getSpeakerNumber(),
                segment.getText()
        );
    }
}
