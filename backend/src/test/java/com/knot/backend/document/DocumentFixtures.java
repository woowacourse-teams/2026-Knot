package com.knot.backend.document;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class DocumentFixtures {
    public static final Instant CREATED_AT = Instant.parse("2026-10-06T00:00:00Z");
    private final JdbcClient jdbc;

    public DocumentFixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long saveMember(String nickname) {
        return jdbc.sql("INSERT INTO members (nickname) VALUES (:nickname) RETURNING id")
                .param(
                        "nickname",
                        nickname
                )
                .query(Long.class)
                .single();
    }

    public long saveWorkspace() {
        return jdbc.sql("INSERT INTO workspaces (name, created_at) VALUES ('문서 팀', :time) RETURNING id")
                .param(
                        "time",
                        Timestamp.from(CREATED_AT)
                )
                .query(Long.class)
                .single();
    }

    public void join(
            long workspaceId,
            long memberId
    ) {
        jdbc.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, 'MEMBER', :time)
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "time",
                        Timestamp.from(CREATED_AT)
                )
                .update();
    }

    public void leave(
            long workspaceId,
            long memberId
    ) {
        jdbc.sql("""
                UPDATE workspace_members SET left_at = :time, last_viewed = false
                WHERE workspace_id = :workspaceId AND member_id = :memberId AND left_at IS NULL
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "time",
                        Timestamp.from(CREATED_AT.plusSeconds(60))
                )
                .update();
    }

    public long saveRecording(
            long workspaceId,
            long memberId,
            long durationMillis
    ) {
        return jdbc.sql("""
                INSERT INTO recording_sessions (workspace_id, member_id, request_id, tab_id, control_token_hash,
                    status, started_at, ended_at, last_seen_at, accumulated_recording_millis)
                VALUES (:workspaceId, :memberId, :requestId, :tabId, :hash, 'ENDED', :start, :end, :end, :duration)
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "requestId",
                        UUID.randomUUID()
                )
                .param(
                        "tabId",
                        UUID.randomUUID()
                )
                .param(
                        "hash",
                        "a".repeat(64)
                )
                .param(
                        "start",
                        Timestamp.from(CREATED_AT.minusSeconds(3600))
                )
                .param(
                        "end",
                        Timestamp.from(CREATED_AT)
                )
                .param(
                        "duration",
                        durationMillis
                )
                .query(Long.class)
                .single();
    }

    public long saveTranscript(long recordingId) {
        return jdbc.sql("""
                INSERT INTO transcripts (recording_session_id, content, created_at)
                VALUES (:recordingId, '전체 녹음 원문', :time) RETURNING id
                """)
                .param(
                        "recordingId",
                        recordingId
                )
                .param(
                        "time",
                        Timestamp.from(CREATED_AT)
                )
                .query(Long.class)
                .single();
    }

    public long saveJob(
            long transcriptId,
            String status
    ) {
        return jdbc.sql("""
                INSERT INTO document_generation_jobs (transcript_id, status, created_at, updated_at)
                VALUES (:transcriptId, :status, :time, :time) RETURNING id
                """)
                .param(
                        "transcriptId",
                        transcriptId
                )
                .param(
                        "status",
                        status
                )
                .param(
                        "time",
                        Timestamp.from(CREATED_AT)
                )
                .query(Long.class)
                .single();
    }

    public long saveDocument(
            long workspaceId,
            long recordingId,
            long transcriptId,
            long jobId,
            String topic
    ) {
        return jdbc.sql("""
                INSERT INTO documents (workspace_id, recording_session_id, source_transcript_id,
                    document_generation_job_id, topic, title, content, status, created_at)
                VALUES (:workspaceId, :recordingId, :transcriptId, :jobId, :topic,
                    '문서 보관 정책', '# 읽기 전용 본문', 'DRAFT', :time) RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "recordingId",
                        recordingId
                )
                .param(
                        "transcriptId",
                        transcriptId
                )
                .param(
                        "jobId",
                        jobId
                )
                .param(
                        "topic",
                        topic
                )
                .param(
                        "time",
                        Timestamp.from(CREATED_AT)
                )
                .query(Long.class)
                .single();
    }

    public void target(
            long documentId,
            long memberId,
            Instant confirmedAt
    ) {
        jdbc.sql("""
                INSERT INTO document_confirmations (document_id, member_id, confirmed_at)
                VALUES (:documentId, :memberId, :confirmedAt)
                """)
                .param(
                        "documentId",
                        documentId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "confirmedAt",
                        nullableTimestamp(confirmedAt)
                )
                .update();
    }

    public String snapshot() {
        return jdbc.sql("""
                SELECT jsonb_build_object(
                    'documents', (SELECT jsonb_agg(to_jsonb(d) ORDER BY id) FROM documents d),
                    'confirmations', (SELECT jsonb_agg(to_jsonb(c) ORDER BY document_id, member_id)
                        FROM document_confirmations c))::text
                """)
                .query(String.class)
                .single();
    }

    private Timestamp nullableTimestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp.from(instant);
    }
}
