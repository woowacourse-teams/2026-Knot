package com.knot.backend.document.application.dto.result;

import com.knot.backend.recording.application.dto.result.TranscriptSegmentResult;
import java.util.List;

public record DocumentTranscriptSnapshot(
        long transcriptId,
        long recordingDurationMillis,
        String transcriptText,
        List<TranscriptSegmentResult> segments
) {

    public DocumentTranscriptSnapshot {
        segments = List.copyOf(segments);
    }
}
