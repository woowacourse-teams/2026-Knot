package com.knot.backend.workspace.infrastructure.persistence;

import com.knot.backend.workspace.domain.ImportedPage;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ImportedPageJpaRepository extends JpaRepository<ImportedPage, Long> {

    long countByWorkspaceIdAndImportRunId(
            Long workspaceId,
            Long importRunId
    );

    @Modifying
    @Query(value = """
            INSERT INTO imported_page_publications (
                workspace_id,
                published_import_run_id,
                published_at
            ) VALUES (
                :workspaceId,
                :importRunId,
                :publishedAt
            )
            ON CONFLICT (workspace_id) DO UPDATE
            SET published_import_run_id = EXCLUDED.published_import_run_id,
                published_at = EXCLUDED.published_at
            """, nativeQuery = true)
    void publish(
            @Param("workspaceId") Long workspaceId,
            @Param("importRunId") Long importRunId,
            @Param("publishedAt") Instant publishedAt
    );

    @Query(value = """
            SELECT
                page.id AS "id",
                page.workspace_id AS "workspaceId",
                parent.id AS "parentId",
                page.title AS "title",
                page.position AS "position",
                page.source_url AS "sourceUrl"
            FROM imported_page_publications publication
            JOIN content_import_runs import_run
                ON import_run.id = publication.published_import_run_id
                AND import_run.workspace_id = publication.workspace_id
                AND import_run.status = 'COMPLETED'
            JOIN imported_pages page
                ON page.import_run_id = publication.published_import_run_id
                AND page.workspace_id = publication.workspace_id
            LEFT JOIN imported_pages parent
                ON parent.workspace_id = page.workspace_id
                AND parent.import_run_id = page.import_run_id
                AND parent.external_page_id = page.parent_external_page_id
            WHERE publication.workspace_id = :workspaceId
            ORDER BY page.position ASC, page.id ASC
            """, nativeQuery = true)
    List<ImportedPageMetadataProjection> findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(
            @Param("workspaceId") Long workspaceId
    );
}
