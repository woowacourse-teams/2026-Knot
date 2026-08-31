package com.knot.backend.workspace.infrastructure.notion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.domain.NotionPage;
import com.knot.backend.workspace.domain.NotionPageRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@Import({TestcontainersConfiguration.class, NotionPageRepositoryAdapter.class})
@DataJpaTest
class NotionPageRepositoryIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @Autowired
    private NotionPageRepository notionPageRepository;
    @Autowired
    private NotionPageJpaRepository notionPageJpaRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbcClient;

    @DisplayName("Workspace의 Page만 position과 ID 오름차순으로 조회한다")
    @Test
    void findAllByWorkspaceIdOrderByPositionAscIdAsc_success() {
        // given
        long workspaceId = saveWorkspace("Knot 팀");
        long otherWorkspaceId = saveWorkspace("다른 팀");
        NotionPage firstPage = savePage(
                workspaceId,
                "first",
                null,
                "첫 Page",
                0
        );
        NotionPage tiedPage = savePage(
                workspaceId,
                "tied",
                null,
                "같은 순서 Page",
                0
        );
        NotionPage lastPage = savePage(
                workspaceId,
                "last",
                firstPage.getId(),
                "마지막 Page",
                2
        );
        savePage(
                otherWorkspaceId,
                "other",
                null,
                "다른 Workspace Page",
                0
        );
        entityManager.flush();
        entityManager.clear();

        // when
        List<NotionPage> result = notionPageRepository.findAllByWorkspaceIdOrderByPositionAscIdAsc(workspaceId);

        // then
        assertThat(result).extracting(
                NotionPage::getId,
                NotionPage::getTitle,
                NotionPage::getParentPageId,
                NotionPage::getPosition
        )
                .containsExactly(
                        tuple(
                                firstPage.getId(),
                                "첫 Page",
                                null,
                                0
                        ),
                        tuple(
                                tiedPage.getId(),
                                "같은 순서 Page",
                                null,
                                0
                        ),
                        tuple(
                                lastPage.getId(),
                                "마지막 Page",
                                firstPage.getId(),
                                2
                        )
                );
    }

    @DisplayName("부모 Page와 자식 Page의 Workspace가 다르면 저장할 수 없다")
    @Test
    void save_failure_parentTenantMismatch() {
        // given
        long workspaceId = saveWorkspace("Knot 팀");
        long otherWorkspaceId = saveWorkspace("다른 팀");
        NotionPage parentPage = savePage(
                workspaceId,
                "parent",
                null,
                "부모",
                0
        );
        entityManager.flush();
        NotionPage childPage = notionPage(
                otherWorkspaceId,
                "child",
                parentPage.getId(),
                "자식",
                0
        );

        // when
        ThrowingCallable action = () -> notionPageJpaRepository.saveAndFlush(childPage);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("존재하지 않는 부모 Page를 참조하면 저장할 수 없다")
    @Test
    void save_failure_missingParent() {
        // given
        long workspaceId = saveWorkspace("Knot 팀");
        NotionPage childPage = notionPage(
                workspaceId,
                "child",
                Long.MAX_VALUE,
                "자식",
                0
        );

        // when
        ThrowingCallable action = () -> notionPageJpaRepository.saveAndFlush(childPage);

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("Page 저장 제약과 조회 순서 인덱스를 제공한다")
    @Test
    void schema_success_constraintsAndQueryIndex() {
        // given
        String indexName = "idx_notion_pages_workspace_order";

        // when
        String indexDefinition = jdbcClient.sql("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = current_schema()
                    AND tablename = 'notion_pages'
                    AND indexname = :indexName
                """)
                .param(
                        "indexName",
                        indexName
                )
                .query(String.class)
                .single();

        // then
        assertThat(indexDefinition).contains("(workspace_id, \"position\", id)");
    }

    private NotionPage savePage(
            long workspaceId,
            String notionPageId,
            Long parentPageId,
            String title,
            int position
    ) {
        NotionPage notionPage = notionPage(
                workspaceId,
                notionPageId,
                parentPageId,
                title,
                position
        );
        entityManager.persist(notionPage);
        entityManager.flush();
        return notionPage;
    }

    private NotionPage notionPage(
            long workspaceId,
            String notionPageId,
            Long parentPageId,
            String title,
            int position
    ) {
        return NotionPage.create(
                workspaceId,
                notionPageId,
                parentPageId,
                title,
                "# " + title,
                position,
                "https://www.notion.so/" + notionPageId,
                CREATED_AT,
                CREATED_AT
        );
    }

    private long saveWorkspace(String name) {
        return jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES (:name, CAST(:createdAt AS TIMESTAMPTZ))
                RETURNING id
                """)
                .param(
                        "name",
                        name
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }
}
