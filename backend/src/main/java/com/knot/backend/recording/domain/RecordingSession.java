package com.knot.backend.recording.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "recording_sessions")
public class RecordingSession {
    private static final Pattern SHA_256_HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "tab_id", nullable = false)
    private UUID tabId;

    @Column(name = "control_token_hash", nullable = false, length = 64)
    private String controlTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordingStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "current_interval_started_at")
    private Instant currentIntervalStartedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "accumulated_recording_millis", nullable = false)
    private long accumulatedRecordingMillis;

    protected RecordingSession() {}

    private RecordingSession(
            Long workspaceId,
            Long memberId,
            UUID requestId,
            UUID tabId,
            String controlTokenHash,
            Instant startedAt
    ) {
        validateStart(
                workspaceId,
                memberId,
                requestId,
                tabId,
                controlTokenHash,
                startedAt
        );
        this.workspaceId = workspaceId;
        this.memberId = memberId;
        this.requestId = requestId;
        this.tabId = tabId;
        this.controlTokenHash = controlTokenHash;
        this.status = RecordingStatus.RECORDING;
        this.startedAt = startedAt;
        this.currentIntervalStartedAt = startedAt;
        this.lastSeenAt = startedAt;
        this.accumulatedRecordingMillis = 0L;
    }

    public static RecordingSession start(
            Long workspaceId,
            Long memberId,
            UUID requestId,
            UUID tabId,
            String controlTokenHash,
            Instant startedAt
    ) {
        return new RecordingSession(
                workspaceId,
                memberId,
                requestId,
                tabId,
                controlTokenHash,
                startedAt
        );
    }

    public boolean matchesStart(
            Long workspaceId,
            UUID tabId,
            String controlTokenHash
    ) {
        return this.workspaceId.equals(workspaceId) && this.tabId.equals(tabId)
                && sameControlTokenHash(controlTokenHash);
    }

    public void pause(Instant pausedAt) {
        ensureNotEnded();
        if (status == RecordingStatus.PAUSED) {
            return;
        }
        validateTimeNotBeforeLastSeen(pausedAt);
        validateTimeNotBeforeCurrentInterval(pausedAt);
        accumulatedRecordingMillis += Duration.between(
                currentIntervalStartedAt,
                pausedAt
        )
                .toMillis();
        status = RecordingStatus.PAUSED;
        currentIntervalStartedAt = null;
        lastSeenAt = pausedAt;
    }

    public void resume(Instant resumedAt) {
        ensureNotEnded();
        if (status == RecordingStatus.RECORDING) {
            return;
        }
        validateTimeNotBeforeLastSeen(resumedAt);
        status = RecordingStatus.RECORDING;
        currentIntervalStartedAt = resumedAt;
        lastSeenAt = resumedAt;
    }

    public void end(Instant endedAt) {
        if (status == RecordingStatus.ENDED) {
            return;
        }
        validateTimeNotBeforeLastSeen(endedAt);
        if (status == RecordingStatus.RECORDING) {
            validateTimeNotBeforeCurrentInterval(endedAt);
            accumulatedRecordingMillis += Duration.between(
                    currentIntervalStartedAt,
                    endedAt
            )
                    .toMillis();
        }
        status = RecordingStatus.ENDED;
        currentIntervalStartedAt = null;
        this.endedAt = endedAt;
        lastSeenAt = endedAt;
    }

    public long getRecordingDurationMillis(Instant now) {
        if (status != RecordingStatus.RECORDING) {
            return accumulatedRecordingMillis;
        }
        validateTimeNotBeforeCurrentInterval(now);
        return accumulatedRecordingMillis + Duration.between(
                currentIntervalStartedAt,
                now
        )
                .toMillis();
    }

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getTabId() {
        return tabId;
    }

    public RecordingStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public long getAccumulatedRecordingMillis() {
        return accumulatedRecordingMillis;
    }

    private boolean sameControlTokenHash(String controlTokenHash) {
        if (!isValidControlTokenHash(controlTokenHash)) {
            return false;
        }
        return MessageDigest.isEqual(
                this.controlTokenHash.getBytes(StandardCharsets.US_ASCII),
                controlTokenHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private void ensureNotEnded() {
        if (status == RecordingStatus.ENDED) {
            throw new RecordingException(RecordingErrorCode.RECORDING_ALREADY_ENDED);
        }
    }

    private void validateTimeNotBeforeCurrentInterval(Instant pointInTime) {
        if (pointInTime == null || pointInTime.isBefore(currentIntervalStartedAt)) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_TIME);
        }
    }

    private void validateTimeNotBeforeLastSeen(Instant pointInTime) {
        if (pointInTime == null || pointInTime.isBefore(lastSeenAt)) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_TIME);
        }
    }

    private void validateStart(
            Long workspaceId,
            Long memberId,
            UUID requestId,
            UUID tabId,
            String controlTokenHash,
            Instant startedAt
    ) {
        if (workspaceId == null || workspaceId <= 0 || memberId == null || memberId <= 0 || requestId == null
                || tabId == null || !isValidControlTokenHash(controlTokenHash)) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_DATA);
        }
        if (startedAt == null) {
            throw new RecordingException(RecordingErrorCode.INVALID_RECORDING_TIME);
        }
    }

    private static boolean isValidControlTokenHash(String controlTokenHash) {
        return controlTokenHash != null && SHA_256_HEX_PATTERN.matcher(controlTokenHash)
                .matches();
    }

}
