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
import lombok.Getter;

@Getter
@Entity
@Table(name = "content_import_runs")
public class ContentImportRun {
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
    private ContentImportStatus status;

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

    protected ContentImportRun() {}

    private ContentImportRun(
            Long workspaceId,
            Long contentSourceConnectionId,
            Long requestedByMemberId,
            ContentImportStatus status,
            Integer totalPageCount,
            int processedPageCount,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt
    ) {
        validateId(workspaceId);
        validateId(contentSourceConnectionId);
        validateId(requestedByMemberId);
        validateStatus(status);
        validatePageCounts(
                totalPageCount,
                processedPageCount
        );
        validateCreatedAt(createdAt);
        validatePendingPageCount(
                status,
                processedPageCount
        );
        validateCompletedPageCounts(
                status,
                totalPageCount,
                processedPageCount
        );
        validateStatusTimestamps(
                status,
                startedAt,
                completedAt,
                createdAt
        );
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

    public static ContentImportRun create(
            Long workspaceId,
            Long contentSourceConnectionId,
            Long requestedByMemberId,
            ContentImportStatus status,
            Integer totalPageCount,
            int processedPageCount,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt
    ) {
        return new ContentImportRun(
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

    public static ContentImportRun createPending(
            Long workspaceId,
            Long contentSourceConnectionId,
            Long requestedByMemberId,
            Instant createdAt
    ) {
        return new ContentImportRun(
                workspaceId,
                contentSourceConnectionId,
                requestedByMemberId,
                ContentImportStatus.PENDING,
                null,
                0,
                null,
                null,
                createdAt
        );
    }
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw invalidImportRun();
        }
    }

    private void validateStatus(ContentImportStatus status) {
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

    private void validatePendingPageCount(
            ContentImportStatus status,
            int processedPageCount
    ) {
        if (status == ContentImportStatus.PENDING && processedPageCount != 0) {
            throw invalidImportRun();
        }
    }

    private void validateCompletedPageCounts(
            ContentImportStatus status,
            Integer totalPageCount,
            int processedPageCount
    ) {
        if (status == ContentImportStatus.COMPLETED
                && (totalPageCount == null || totalPageCount < 1 || processedPageCount != totalPageCount)) {
            throw invalidImportRun();
        }
    }

    private void validateStatusTimestamps(
            ContentImportStatus status,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt
    ) {
        boolean valid = switch (status) {
            case PENDING -> startedAt == null && completedAt == null;
            case RUNNING -> startedAt != null && completedAt == null && !startedAt.isBefore(createdAt);
            case COMPLETED, FAILED -> startedAt != null && completedAt != null && !startedAt.isBefore(createdAt)
                    && !completedAt.isBefore(startedAt);
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

    private ContentImportException invalidImportRun() {
        return new ContentImportException(ContentImportErrorCode.INVALID_CONTENT_IMPORT_RUN);
    }
}
