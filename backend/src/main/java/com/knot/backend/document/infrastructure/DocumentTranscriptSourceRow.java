package com.knot.backend.document.infrastructure;

record DocumentTranscriptSourceRow(
        long transcriptId,
        long recordingDurationMillis,
        String transcriptText
) {

}
