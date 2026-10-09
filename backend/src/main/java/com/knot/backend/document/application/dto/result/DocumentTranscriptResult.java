package com.knot.backend.document.application.dto.result;

import com.knot.backend.recording.application.dto.result.TranscriptSegmentResult;
import java.util.List;

public record DocumentTranscriptResult(
        long transcriptId,
        int recordingDurationSeconds,
        String transcriptText,
        List<TranscriptSegmentResult> segments
) {

    public DocumentTranscriptResult {
        segments = List.copyOf(segments);
    }

    public static DocumentTranscriptResult from(DocumentTranscriptSnapshot snapshot) {
        return new DocumentTranscriptResult(
                snapshot.transcriptId(),
                Math.toIntExact(snapshot.recordingDurationMillis() / 1000),
                snapshot.transcriptText(),
                snapshot.segments()
        );
    }
}
