package com.knot.backend.document.infrastructure;

import com.knot.backend.document.application.DocumentTranscriptQuery;
import com.knot.backend.document.application.dto.result.DocumentTranscriptSnapshot;
import com.knot.backend.recording.application.dto.result.TranscriptSegmentResult;
import com.knot.backend.recording.domain.TranscriptSegmentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentTranscriptQueryAdapter implements DocumentTranscriptQuery {

    private final DocumentTranscriptReadJpaRepository documents;
    private final TranscriptSegmentRepository segments;

    @Override
    public Optional<DocumentTranscriptSnapshot> find(
            long workspaceId,
            long documentId
    ) {
        return documents.findSource(
                workspaceId,
                documentId
        )
                .map(this::toSnapshot);
    }

    private DocumentTranscriptSnapshot toSnapshot(DocumentTranscriptSourceRow row) {
        return new DocumentTranscriptSnapshot(
                row.transcriptId(),
                row.recordingDurationMillis(),
                row.transcriptText(),
                segments.findAllByTranscriptId(row.transcriptId())
                        .stream()
                        .map(TranscriptSegmentResult::from)
                        .toList()
        );
    }
}
