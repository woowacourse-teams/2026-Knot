package com.knot.backend.workspace.infrastructure.notion;

import com.knot.backend.workspace.domain.NotionPage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotionPageJpaRepository extends JpaRepository<NotionPage, Long> {

    @Query(value = """
            SELECT
                page.id AS "id",
                page.workspace_id AS "workspaceId",
                page.parent_page_id AS "parentPageId",
                page.title AS "title",
                page.position AS "position",
                page.notion_url AS "notionUrl"
            FROM notion_page_publications publication
            JOIN notion_import_runs import_run
                ON import_run.id = publication.published_import_run_id
                AND import_run.workspace_id = publication.workspace_id
                AND import_run.status = 'COMPLETED'
            JOIN notion_pages page
                ON page.import_run_id = publication.published_import_run_id
                AND page.workspace_id = publication.workspace_id
            WHERE publication.workspace_id = :workspaceId
            ORDER BY page.position ASC, page.id ASC
            """, nativeQuery = true)
    List<NotionPageMetadataProjection> findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(
            @Param("workspaceId") Long workspaceId
    );
}
