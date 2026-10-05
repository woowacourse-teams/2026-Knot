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
import lombok.AccessLevel;
import lombok.Getter;

@Getter
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

    @Getter(AccessLevel.NONE)
    @Column(name = "control_token_hash", nullable = false, length = 64)
    private String controlTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordingStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Getter(AccessLevel.NONE)
    @Column(name = "current_interval_started_at")
    private Instant currentIntervalStartedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

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

    public void validateControlledBy(
            long workspaceId,
            long memberId
    ) {
        if (this.workspaceId != workspaceId) {
            throw new RecordingException(RecordingErrorCode.RECORDING_NOT_FOUND);
        }
        if (this.memberId != memberId) {
            throw new RecordingException(RecordingErrorCode.RECORDING_CONTROL_DENIED);
        }
    }

    public void validateControlProof(
            UUID tabId,
            String controlTokenHash
    ) {
        if (!this.tabId.equals(tabId) || !sameControlTokenHash(controlTokenHash)) {
            throw new RecordingException(RecordingErrorCode.RECORDING_CONTROL_DENIED);
        }
    }

    public void pause(Instant pausedAt) {
        ensureActive();
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
        this.pausedAt = pausedAt;
        lastSeenAt = pausedAt;
    }

    public void resume(Instant resumedAt) {
        ensureActive();
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
        ensureNotDiscarded();
        stop(
                RecordingStatus.ENDED,
                endedAt
        );
    }

    public void discard(Instant discardedAt) {
        if (status == RecordingStatus.DISCARDED) {
            return;
        }
        ensureNotEnded();
        stop(
                RecordingStatus.DISCARDED,
                notBeforeLastSeen(discardedAt)
        );
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

    private boolean sameControlTokenHash(String controlTokenHash) {
        if (!isValidControlTokenHash(controlTokenHash)) {
            return false;
        }
        return MessageDigest.isEqual(
                this.controlTokenHash.getBytes(StandardCharsets.US_ASCII),
                controlTokenHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private void stop(
            RecordingStatus stoppedStatus,
            Instant stoppedAt
    ) {
        validateTimeNotBeforeLastSeen(stoppedAt);
        if (status == RecordingStatus.RECORDING) {
            validateTimeNotBeforeCurrentInterval(stoppedAt);
            accumulatedRecordingMillis += Duration.between(
                    currentIntervalStartedAt,
                    stoppedAt
            )
                    .toMillis();
        }
        status = stoppedStatus;
        currentIntervalStartedAt = null;
        this.endedAt = stoppedAt;
        lastSeenAt = stoppedAt;
    }

    // 폐기는 사용자가 요청하지 않은 처리라서 서버 시계가 뒤로 조정돼도 탈퇴·승계를 실패시키지 않는다.
    private Instant notBeforeLastSeen(Instant pointInTime) {
        if (pointInTime != null && pointInTime.isBefore(lastSeenAt)) {
            return lastSeenAt;
        }
        return pointInTime;
    }

    private void ensureActive() {
        ensureNotEnded();
        ensureNotDiscarded();
    }

    private void ensureNotEnded() {
        if (status == RecordingStatus.ENDED) {
            throw new RecordingException(RecordingErrorCode.RECORDING_ALREADY_ENDED);
        }
    }

    private void ensureNotDiscarded() {
        if (status == RecordingStatus.DISCARDED) {
            throw new RecordingException(RecordingErrorCode.RECORDING_ALREADY_DISCARDED);
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
