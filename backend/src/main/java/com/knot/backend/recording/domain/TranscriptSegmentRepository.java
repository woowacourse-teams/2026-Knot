package com.knot.backend.recording.domain;

import java.util.List;

public interface TranscriptSegmentRepository {

    void saveAll(List<TranscriptSegment> segments);

    List<TranscriptSegment> findAllByTranscriptId(long transcriptId);
}
