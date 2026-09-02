package com.knot.backend.search.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchChunkRepository;
import com.knot.backend.search.domain.SearchIndexedChunk;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class JdbcSearchChunkRepositoryIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026,
            9,
            1,
            0,
            0,
            0,
            0,
            ZoneOffset.UTC
    );

    private final SearchChunkRepository searchChunkRepository;
    private final JdbcClient jdbcClient;

    JdbcSearchChunkRepositoryIntegrationTest(
            SearchChunkRepository searchChunkRepository,
            JdbcClient jdbcClient
    ) {
        this.searchChunkRepository = searchChunkRepository;
        this.jdbcClient = jdbcClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient.sql("""
                TRUNCATE TABLE
                    search_references,
                    search_document_chunks,
                    imported_page_publications,
                    imported_pages,
                    content_import_runs,
                    content_source_connections,
                    content_source_authorizations,
                    chat_feedback,
                    chat_messages,
                    chat_sessions,
                    workspace_members,
                    workspaces,
                    members
                RESTART IDENTITY CASCADE
                """)
                .update();
    }

    @Test
    @DisplayName("workspace와 마지막 성공 import run으로 검색을 격리하고 vector·keyword 결과를 반환한다")
    void search_success_isolatesWorkspaceAndPublishedRun() {
        // given
        saveWorkspace(
                1L,
                "첫 팀",
                11L,
                101L,
                100L,
                100L,
                10001L,
                "첫 문서"
        );
        saveWorkspace(
                2L,
                "둘째 팀",
                22L,
                202L,
                200L,
                200L,
                20002L,
                "다른 문서"
        );
        searchChunkRepository.replace(
                1L,
                100L,
                List.of(
                        SearchIndexedChunk.of(
                                10001L,
                                100L,
                                0,
                                "PostgreSQL 결정",
                                vector(1)
                        )
                )
        );
        searchChunkRepository.replace(
                2L,
                200L,
                List.of(
                        SearchIndexedChunk.of(
                                20002L,
                                200L,
                                0,
                                "다른 팀 문서",
                                vector(1)
                        )
                )
        );

        // when
        List<SearchChunk> vectorResults = searchChunkRepository.findByVector(
                1L,
                100L,
                vector(1),
                3
        );
        List<SearchChunk> keywordResults = searchChunkRepository.findByKeywords(
                1L,
                100L,
                List.of("PostgreSQL"),
                3
        );

        // then
        assertThat(searchChunkRepository.findPublishedImportRunId(1L)).contains(100L);
        assertThat(searchChunkRepository.findPublishedImportRunId(2L)).contains(200L);
        assertThat(vectorResults).extracting(SearchChunk::workspaceId)
                .containsOnly(1L);
        assertThat(vectorResults).extracting(SearchChunk::title)
                .containsExactly("첫 문서");
        assertThat(keywordResults).extracting(SearchChunk::content)
                .containsExactly("PostgreSQL 결정");
    }

    @Test
    @DisplayName("새로운 비공개 run의 색인은 publication pointer가 바뀌기 전 검색 결과에 노출되지 않는다")
    void search_success_ignoresUnpublishedRun() {
        // given
        saveWorkspace(
                1L,
                "첫 팀",
                11L,
                101L,
                100L,
                100L,
                10001L,
                "기존 문서"
        );
        saveUnpublishedRunAndPage(
                101L,
                10002L,
                "새 문서"
        );
        searchChunkRepository.replace(
                1L,
                100L,
                List.of(
                        SearchIndexedChunk.of(
                                10001L,
                                100L,
                                0,
                                "기존 내용",
                                vector(1)
                        )
                )
        );
        searchChunkRepository.replace(
                1L,
                101L,
                List.of(
                        SearchIndexedChunk.of(
                                10002L,
                                101L,
                                0,
                                "새 내용",
                                vector(1)
                        )
                )
        );

        // when
        List<SearchChunk> results = searchChunkRepository.findByKeywords(
                1L,
                searchChunkRepository.findPublishedImportRunId(1L)
                        .orElseThrow(),
                List.of("내용"),
                3
        );

        // then
        assertThat(results).extracting(SearchChunk::title)
                .containsExactly("기존 문서");
    }

    private void saveWorkspace(
            Long workspaceId,
            String workspaceName,
            Long memberId,
            Long connectionId,
            Long importRunId,
            Long publishedRunId,
            Long pageId,
            String title
    ) {
        jdbcClient.sql("""
                INSERT INTO members (id, nickname, profile_image_url)
                OVERRIDING SYSTEM VALUE
                VALUES (:id, :nickname, NULL)
                """)
                .param(
                        "id",
                        memberId
                )
                .param(
                        "nickname",
                        "member-" + memberId
                )
                .update();
        jdbcClient.sql("""
                INSERT INTO workspaces (id, name, created_at)
                OVERRIDING SYSTEM VALUE
                VALUES (:id, :name, :createdAt)
                """)
                .param(
                        "id",
                        workspaceId
                )
                .param(
                        "name",
                        workspaceName
                )
                .param(
                        "createdAt",
                        NOW
                )
                .update();
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, 'OWNER', :joinedAt)
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
                        "joinedAt",
                        NOW
                )
                .update();
        jdbcClient.sql("""
                INSERT INTO content_source_connections (
                    id, workspace_id, provider, access_credential_ciphertext,
                    external_source_id, provider_connection_id, authorization_owner_type,
                    authorizing_member_id, created_at, updated_at, version
                ) OVERRIDING SYSTEM VALUE VALUES (
                    :id, :workspaceId, 'NOTION', 'ciphertext',
                    :externalSourceId, :providerConnectionId, 'WORKSPACE',
                    :memberId, :createdAt, :updatedAt, 0
                )
                """)
                .param(
                        "id",
                        connectionId
                )
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "externalSourceId",
                        "source-" + workspaceId
                )
                .param(
                        "providerConnectionId",
                        "connection-" + workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "createdAt",
                        NOW
                )
                .param(
                        "updatedAt",
                        NOW
                )
                .update();
        jdbcClient.sql("""
                INSERT INTO content_import_runs (
                    id, workspace_id, content_source_connection_id, requested_by_member_id,
                    status, total_page_count, processed_page_count, started_at, completed_at, created_at
                ) OVERRIDING SYSTEM VALUE VALUES (
                    :id, :workspaceId, :connectionId, :memberId,
                    'COMPLETED', 1, 1, :startedAt, :completedAt, :createdAt
                )
                """)
                .param(
                        "id",
                        importRunId
                )
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "connectionId",
                        connectionId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "startedAt",
                        NOW
                )
                .param(
                        "completedAt",
                        NOW.plusSeconds(1)
                )
                .param(
                        "createdAt",
                        NOW
                )
                .update();
        jdbcClient.sql("""
                INSERT INTO imported_pages (
                    id, workspace_id, import_run_id, external_page_id, title,
                    markdown_content, position, source_url, created_at, updated_at
                ) OVERRIDING SYSTEM VALUE VALUES (
                    :id, :workspaceId, :importRunId, :externalPageId, :title,
                    :markdownContent, 0, :sourceUrl, :createdAt, :updatedAt
                )
                """)
                .param(
                        "id",
                        pageId
                )
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "importRunId",
                        importRunId
                )
                .param(
                        "externalPageId",
                        "external-" + pageId
                )
                .param(
                        "title",
                        title
                )
                .param(
                        "markdownContent",
                        title + " 본문"
                )
                .param(
                        "sourceUrl",
                        "https://notion.test/" + pageId
                )
                .param(
                        "createdAt",
                        NOW
                )
                .param(
                        "updatedAt",
                        NOW
                )
                .update();
        jdbcClient.sql("""
                INSERT INTO imported_page_publications (workspace_id, published_import_run_id, published_at)
                VALUES (:workspaceId, :importRunId, :publishedAt)
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "importRunId",
                        publishedRunId
                )
                .param(
                        "publishedAt",
                        NOW.plusSeconds(2)
                )
                .update();
    }

    private void saveUnpublishedRunAndPage(
            Long importRunId,
            Long pageId,
            String title
    ) {
        jdbcClient.sql("""
                INSERT INTO content_import_runs (
                    id, workspace_id, content_source_connection_id, requested_by_member_id,
                    status, total_page_count, processed_page_count, started_at, completed_at, created_at
                ) OVERRIDING SYSTEM VALUE VALUES (
                    101, 1, 101, 11, 'COMPLETED', 1, 1,
                    :startedAt, :completedAt, :createdAt
                )
                """)
                .param(
                        "startedAt",
                        NOW.plusSeconds(3)
                )
                .param(
                        "completedAt",
                        NOW.plusSeconds(4)
                )
                .param(
                        "createdAt",
                        NOW.plusSeconds(3)
                )
                .update();
        jdbcClient.sql("""
                INSERT INTO imported_pages (
                    id, workspace_id, import_run_id, external_page_id, title,
                    markdown_content, position, source_url, created_at, updated_at
                ) OVERRIDING SYSTEM VALUE VALUES (:id, 1, :importRunId, :externalPageId, :title,
                    :markdownContent, 0, :sourceUrl, :createdAt, :updatedAt)
                """)
                .param(
                        "id",
                        pageId
                )
                .param(
                        "importRunId",
                        importRunId
                )
                .param(
                        "externalPageId",
                        "external-" + pageId
                )
                .param(
                        "title",
                        title
                )
                .param(
                        "markdownContent",
                        title + " 본문"
                )
                .param(
                        "sourceUrl",
                        "https://notion.test/" + pageId
                )
                .param(
                        "createdAt",
                        NOW.plusSeconds(3)
                )
                .param(
                        "updatedAt",
                        NOW.plusSeconds(3)
                )
                .update();
    }

    private double[] vector(int activeIndex) {
        double[] vector = new double[1024];
        vector[activeIndex] = 1;
        return vector;
    }
}
