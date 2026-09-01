package com.knot.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;

@Getter
@Entity
@Table(name = "notion_import_runs")
public class NotionImportRun {
    private static final String PUBLIC_FAILURE_REASON = "Notion 문서를 가져오지 못했습니다";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Column(name = "content_source_connection_id", nullable = false, updatable = false)
    private Long contentSourceConnectionId;

    @Column(name = "requested_by_member_id", nullable = false, updatable = false)
    private Long requestedByMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotionImportStatus status;

    @Column(name = "total_page_count")
    private Integer totalPageCount;

    @Column(name = "processed_page_count", nullable = false)
    private int processedPageCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotionImportRun() {}

    private NotionImportRun(
            Long workspaceId,
            Long contentSourceConnectionId,
            Long requestedByMemberId,
            NotionImportStatus status,
            Integer totalPageCount,
            int processedPageCount,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt
    ) {
        validateId(workspaceId);
        validateId(contentSourceConnectionId);
        validateId(requestedByMemberId);
        validateRequiredStatus(status);
        validatePageCounts(
                totalPageCount,
                processedPageCount
        );
        validateStatusTimestamps(
                status,
                startedAt,
                completedAt
        );
        validateCreatedAt(createdAt);
        this.workspaceId = workspaceId;
        this.contentSourceConnectionId = contentSourceConnectionId;
        this.requestedByMemberId = requestedByMemberId;
        this.status = status;
        this.totalPageCount = totalPageCount;
        this.processedPageCount = processedPageCount;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
    }

    public static NotionImportRun create(
            Long workspaceId,
            Long contentSourceConnectionId,
            Long requestedByMemberId,
            NotionImportStatus status,
            Integer totalPageCount,
            int processedPageCount,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt
    ) {
        return new NotionImportRun(
                workspaceId,
                contentSourceConnectionId,
                requestedByMemberId,
                status,
                totalPageCount,
                processedPageCount,
                startedAt,
                completedAt,
                createdAt
        );
    }

    public String publicFailureReason() {
        if (status == NotionImportStatus.FAILED) {
            return PUBLIC_FAILURE_REASON;
        }
        return null;
    }

    public void start(Instant startedAt) {
        validateStatus(NotionImportStatus.PENDING);
        validateTransitionTime(startedAt);
        this.status = NotionImportStatus.RUNNING;
        this.startedAt = truncateToDatabasePrecision(startedAt);
    }

    public void preparePageCount(int totalPageCount) {
        validateStatus(NotionImportStatus.RUNNING);
        if (totalPageCount <= 0 || this.totalPageCount != null || processedPageCount != 0) {
            throw invalidImportRun();
        }
        this.totalPageCount = totalPageCount;
    }

    public void recordProcessedPage() {
        validateStatus(NotionImportStatus.RUNNING);
        if (totalPageCount == null || processedPageCount >= totalPageCount) {
            throw invalidImportRun();
        }
        processedPageCount++;
    }

    public void complete(Instant completedAt) {
        validateStatus(NotionImportStatus.RUNNING);
        validateTransitionTime(completedAt);
        if (totalPageCount == null || totalPageCount <= 0 || processedPageCount != totalPageCount) {
            throw invalidImportRun();
        }
        this.status = NotionImportStatus.COMPLETED;
        this.completedAt = truncateToDatabasePrecision(completedAt);
    }

    public void fail(Instant failedAt) {
        if (status != NotionImportStatus.PENDING && status != NotionImportStatus.RUNNING) {
            throw invalidImportRun();
        }
        validateTransitionTime(failedAt);
        Instant normalizedFailedAt = truncateToDatabasePrecision(failedAt);
        if (status == NotionImportStatus.PENDING) {
            this.startedAt = normalizedFailedAt;
        }
        this.status = NotionImportStatus.FAILED;
        this.completedAt = normalizedFailedAt;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw invalidImportRun();
        }
    }

    private void validateRequiredStatus(NotionImportStatus status) {
        if (status == null) {
            throw invalidImportRun();
        }
    }

    private void validatePageCounts(
            Integer totalPageCount,
            int processedPageCount
    ) {
        if (totalPageCount != null && totalPageCount < 0) {
            throw invalidImportRun();
        }
        if (processedPageCount < 0 || totalPageCount != null && processedPageCount > totalPageCount) {
            throw invalidImportRun();
        }
    }

    private void validateStatusTimestamps(
            NotionImportStatus status,
            Instant startedAt,
            Instant completedAt
    ) {
        boolean valid = switch (status) {
            case PENDING -> startedAt == null && completedAt == null;
            case RUNNING -> startedAt != null && completedAt == null;
            case COMPLETED, FAILED -> startedAt != null && completedAt != null;
        };
        if (!valid) {
            throw invalidImportRun();
        }
    }

    private void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw invalidImportRun();
        }
    }

    private void validateStatus(NotionImportStatus expectedStatus) {
        if (status != expectedStatus) {
            throw invalidImportRun();
        }
    }

    private void validateTransitionTime(Instant transitionTime) {
        if (transitionTime == null || transitionTime.isBefore(createdAt)
                || startedAt != null && transitionTime.isBefore(startedAt)) {
            throw invalidImportRun();
        }
    }

    private NotionImportException invalidImportRun() {
        return new NotionImportException(NotionImportErrorCode.INVALID_NOTION_IMPORT_RUN);
    }

    private Instant truncateToDatabasePrecision(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}
