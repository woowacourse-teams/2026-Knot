package com.knot.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "imported_pages")
public class ImportedPage {
    public static final int MAX_EXTERNAL_PAGE_ID_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Column(name = "import_run_id", nullable = false, updatable = false)
    private Long importRunId;

    @Column(name = "external_page_id", nullable = false, length = MAX_EXTERNAL_PAGE_ID_LENGTH, updatable = false)
    private String externalPageId;

    @Column(name = "parent_external_page_id", length = MAX_EXTERNAL_PAGE_ID_LENGTH, updatable = false)
    private String parentExternalPageId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "markdown_content", nullable = false, columnDefinition = "TEXT")
    private String markdownContent;

    @Column(nullable = false)
    private int position;

    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImportedPage() {}

    private ImportedPage(
            Long workspaceId,
            Long importRunId,
            String externalPageId,
            String parentExternalPageId,
            String title,
            String markdownContent,
            int position,
            String sourceUrl,
            Instant createdAt,
            Instant updatedAt
    ) {
        validateWorkspaceId(workspaceId);
        validateImportRunId(importRunId);
        validateExternalPageId(externalPageId);
        validateParentExternalPageId(
                externalPageId,
                parentExternalPageId
        );
        validateTitle(title);
        validateMarkdownContent(markdownContent);
        validatePosition(position);
        validateSourceUrl(sourceUrl);
        validateTimestamps(
                createdAt,
                updatedAt
        );
        this.workspaceId = workspaceId;
        this.importRunId = importRunId;
        this.externalPageId = externalPageId;
        this.parentExternalPageId = parentExternalPageId;
        this.title = title;
        this.markdownContent = markdownContent;
        this.position = position;
        this.sourceUrl = sourceUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ImportedPage create(
            Long workspaceId,
            Long importRunId,
            String externalPageId,
            String parentExternalPageId,
            String title,
            String markdownContent,
            int position,
            String sourceUrl,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new ImportedPage(
                workspaceId,
                importRunId,
                externalPageId,
                parentExternalPageId,
                title,
                markdownContent,
                position,
                sourceUrl,
                createdAt,
                updatedAt
        );
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw invalidImportedPage();
        }
    }

    private void validateImportRunId(Long importRunId) {
        if (importRunId == null || importRunId <= 0) {
            throw invalidImportedPage();
        }
    }

    private void validateExternalPageId(String externalPageId) {
        if (externalPageId == null || externalPageId.isBlank()
                || externalPageId.length() > MAX_EXTERNAL_PAGE_ID_LENGTH) {
            throw invalidImportedPage();
        }
    }

    private void validateParentExternalPageId(
            String externalPageId,
            String parentExternalPageId
    ) {
        if (parentExternalPageId != null
                && (parentExternalPageId.isBlank() || parentExternalPageId.length() > MAX_EXTERNAL_PAGE_ID_LENGTH
                        || parentExternalPageId.equals(externalPageId))) {
            throw invalidImportedPage();
        }
    }

    private void validateTitle(String title) {
        if (title == null) {
            throw invalidImportedPage();
        }
    }

    private void validateMarkdownContent(String markdownContent) {
        if (markdownContent == null) {
            throw invalidImportedPage();
        }
    }

    private void validatePosition(int position) {
        if (position < 0) {
            throw invalidImportedPage();
        }
    }

    private void validateSourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw invalidImportedPage();
        }
    }

    private void validateTimestamps(
            Instant createdAt,
            Instant updatedAt
    ) {
        if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
            throw invalidImportedPage();
        }
    }

    private ImportedPageException invalidImportedPage() {
        return new ImportedPageException(ImportedPageErrorCode.INVALID_IMPORTED_PAGE);
    }
}
