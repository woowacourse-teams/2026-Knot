package com.knot.backend.search;

import com.knot.backend.document.DocumentFixtures;
import org.springframework.jdbc.core.simple.JdbcClient;

public class SearchMessageFixtures {

    private final JdbcClient jdbc;
    private final DocumentFixtures documents;

    public SearchMessageFixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.documents = new DocumentFixtures(jdbc);
    }

    public long messageId(
            long conversationId,
            int sequence
    ) {
        return jdbc.sql("SELECT id FROM search_messages WHERE conversation_id = :id AND sequence = :sequence")
                .param(
                        "id",
                        conversationId
                )
                .param(
                        "sequence",
                        sequence
                )
                .query(Long.class)
                .single();
    }

    public long saveDocument(
            long workspaceId,
            long memberId
    ) {
        long recordingId = documents.saveRecording(
                workspaceId,
                memberId,
                60000
        );
        long transcriptId = documents.saveTranscript(recordingId);
        long jobId = documents.saveJob(
                transcriptId,
                "SUCCEEDED"
        );
        return documents.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                jobId,
                "탐색 테스트"
        );
    }

    public void saveEvidence(
            long messageId,
            long documentId,
            int rank
    ) {
        jdbc.sql("INSERT INTO search_evidences (message_id, document_id, rank) VALUES (:message, :document, :rank)")
                .param(
                        "message",
                        messageId
                )
                .param(
                        "document",
                        documentId
                )
                .param(
                        "rank",
                        rank
                )
                .update();
    }
}
