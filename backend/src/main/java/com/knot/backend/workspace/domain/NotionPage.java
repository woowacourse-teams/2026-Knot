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
@Table(name = "notion_pages")
public class NotionPage {
    public static final int MAX_NOTION_PAGE_ID_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Column(name = "import_run_id", nullable = false, updatable = false)
    private Long importRunId;

    @Column(name = "notion_page_id", nullable = false, length = MAX_NOTION_PAGE_ID_LENGTH, updatable = false)
    private String notionPageId;

    @Column(name = "parent_page_id")
    private Long parentPageId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "markdown_content", nullable = false, columnDefinition = "TEXT")
    private String markdownContent;

    @Column(nullable = false)
    private int position;

    @Column(name = "notion_url", nullable = false, columnDefinition = "TEXT")
    private String notionUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotionPage() {}

    private NotionPage(
            Long workspaceId,
            Long importRunId,
            String notionPageId,
            Long parentPageId,
            String title,
            String markdownContent,
            int position,
            String notionUrl,
            Instant createdAt,
            Instant updatedAt
    ) {
        validateWorkspaceId(workspaceId);
        validateImportRunId(importRunId);
        validateNotionPageId(notionPageId);
        validateParentPageId(parentPageId);
        validateTitle(title);
        validateMarkdownContent(markdownContent);
        validatePosition(position);
        validateNotionUrl(notionUrl);
        validateTimestamps(
                createdAt,
                updatedAt
        );
        this.workspaceId = workspaceId;
        this.importRunId = importRunId;
        this.notionPageId = notionPageId;
        this.parentPageId = parentPageId;
        this.title = title;
        this.markdownContent = markdownContent;
        this.position = position;
        this.notionUrl = notionUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static NotionPage create(
            Long workspaceId,
            Long importRunId,
            String notionPageId,
            Long parentPageId,
            String title,
            String markdownContent,
            int position,
            String notionUrl,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new NotionPage(
                workspaceId,
                importRunId,
                notionPageId,
                parentPageId,
                title,
                markdownContent,
                position,
                notionUrl,
                createdAt,
                updatedAt
        );
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw invalidNotionPage();
        }
    }

    private void validateImportRunId(Long importRunId) {
        if (importRunId == null || importRunId <= 0) {
            throw invalidNotionPage();
        }
    }

    private void validateNotionPageId(String notionPageId) {
        if (notionPageId == null || notionPageId.isBlank() || notionPageId.length() > MAX_NOTION_PAGE_ID_LENGTH) {
            throw invalidNotionPage();
        }
    }

    private void validateParentPageId(Long parentPageId) {
        if (parentPageId != null && parentPageId <= 0) {
            throw invalidNotionPage();
        }
    }

    private void validateTitle(String title) {
        if (title == null) {
            throw invalidNotionPage();
        }
    }

    private void validateMarkdownContent(String markdownContent) {
        if (markdownContent == null) {
            throw invalidNotionPage();
        }
    }

    private void validatePosition(int position) {
        if (position < 0) {
            throw invalidNotionPage();
        }
    }

    private void validateNotionUrl(String notionUrl) {
        if (notionUrl == null || notionUrl.isBlank()) {
            throw invalidNotionPage();
        }
    }

    private void validateTimestamps(
            Instant createdAt,
            Instant updatedAt
    ) {
        if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
            throw invalidNotionPage();
        }
    }

    private NotionPageException invalidNotionPage() {
        return new NotionPageException(NotionPageErrorCode.INVALID_NOTION_PAGE);
    }
}
