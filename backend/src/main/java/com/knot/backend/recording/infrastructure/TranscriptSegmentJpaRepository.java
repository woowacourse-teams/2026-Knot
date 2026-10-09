package com.knot.backend.recording.infrastructure;

import com.knot.backend.recording.domain.TranscriptSegment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TranscriptSegmentJpaRepository extends JpaRepository<TranscriptSegment, Long> {

    List<TranscriptSegment> findAllByTranscriptIdOrderByStartMillisAscPositionAsc(long transcriptId);
}
