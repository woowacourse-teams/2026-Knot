package com.knot.backend.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.search.application.DocumentEmbeddingClient;
import com.knot.backend.search.application.EmbeddingTask;
import com.knot.backend.search.domain.SearchChunkRepository;
import com.knot.backend.search.domain.SearchIndexedChunk;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Workspace 검색 API(로드맵 S7)는 세션 검색(S1)과 같은 검색·선별·예산 로직을 쓰되 아무것도 저장하지 않는다. 모든
 * 시나리오에서 chat_sessions·chat_messages·search_references 행 수가 0으로 남는지 함께 확인한다.
 */
@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkspaceSearchAcceptanceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T00:00:00Z");
    private static final OffsetDateTime CREATED_AT_OFFSET = CREATED_AT.atOffset(ZoneOffset.UTC);
    private static final String QUESTION = "PostgreSQL을 왜 사용했나요?";
    private static final String SEARCH_PATH = "/api/v1/workspaces/{workspaceId}/search";

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;
    private final SearchChunkRepository searchChunkRepository;
    private final DocumentEmbeddingClient embeddingClient;

    WorkspaceSearchAcceptanceTest(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            JdbcClient jdbcClient,
            SearchChunkRepository searchChunkRepository,
            DocumentEmbeddingClient embeddingClient
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.jdbcClient = jdbcClient;
        this.searchChunkRepository = searchChunkRepository;
        this.embeddingClient = embeddingClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient.sql("""
                TRUNCATE TABLE search_references, search_document_chunks, chat_feedback, chat_messages, chat_sessions,
                    imported_page_publications, imported_pages, content_import_runs,
                    content_source_connections, content_source_authorizations,
                    workspace_members, workspaces, members RESTART IDENTITY CASCADE
                """)
                .update();
    }

    @Test
    @DisplayName("멤버가 근거를 찾으면 같은 페이지를 포함한 청크 상위 8개와 규칙 문장을 반환하고 아무것도 저장하지 않는다")
    void search_success_readyReturnsTopEightChunksWithoutSaving() throws Exception {
        // given
        Fixture fixture = saveFixture(true);
        indexChunks(
                fixture,
                List.of(
                        4,
                        3,
                        2
                ),
                QUESTION
        );

        // when
        ResultActions result = perform(
                fixture.workspaceId(),
                fixture.memberId(),
                QUESTION
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.groundingRules").value(org.hamcrest.Matchers.containsString("근거 문서")))
                .andExpect(jsonPath("$.fallbackAnswer").doesNotExist())
                .andExpect(jsonPath("$.userMessageId").doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.chunks",
                                hasSize(8)
                        )
                )
                .andExpect(jsonPath("$.chunks[0].importRunId").value(fixture.importRunId()))
                .andExpect(jsonPath("$.chunks[0].chunkIndex").isNumber())
                .andExpect(jsonPath("$.chunks[0].title").isString())
                .andExpect(
                        jsonPath("$.chunks[0].sourceUrl")
                                .value(org.hamcrest.Matchers.startsWith("https://notion.test/"))
                )
                .andExpect(jsonPath("$.chunks[0].content").value(org.hamcrest.Matchers.containsString(QUESTION)))
                .andExpect(
                        jsonPath(
                                "$.chunks[?(@.importedPageId == " + fixture.pageIds()
                                        .getFirst() + ")]",
                                hasSize(greaterThanOrEqualTo(2))
                        )
                );
        String body = result.andReturn()
                .getResponse()
                .getContentAsString();
        List<Double> scores = JsonPath.read(
                body,
                "$.chunks[*].score"
        );
        assertThat(scores).isSortedAccordingTo(Comparator.reverseOrder())
                .allSatisfy(
                        score -> assertThat(score).isBetween(
                                0.35,
                                1.0
                        )
                );
        assertNothingSaved();
    }

    @Test
    @DisplayName("같은 질문을 연속으로 보내도 진행 중 턴 검사 없이 매번 200을 반환한다")
    void search_success_repeatedSearchIsNotBlocked() throws Exception {
        // given
        Fixture fixture = saveFixture(true);
        indexChunks(
                fixture,
                List.of(1),
                QUESTION
        );

        // when
        perform(
                fixture.workspaceId(),
                fixture.memberId(),
                QUESTION
        ).andExpect(status().isOk());
        ResultActions second = perform(
                fixture.workspaceId(),
                fixture.memberId(),
                QUESTION
        );

        // then
        second.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        assertNothingSaved();
    }

    @Test
    @DisplayName("관련 문서가 없으면 NO_RESULT와 안내 문구만 반환하고 저장하지 않는다")
    void search_success_noResult() throws Exception {
        // given
        Fixture fixture = saveFixture(true);
        indexChunks(
                fixture,
                List.of(1),
                "점심 메뉴 추천 목록"
        );

        // when
        ResultActions result = perform(
                fixture.workspaceId(),
                fixture.memberId(),
                "Redis 도입 근거"
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_RESULT"))
                .andExpect(jsonPath("$.fallbackAnswer").value(org.hamcrest.Matchers.containsString("찾지 못했습니다")))
                .andExpect(jsonPath("$.groundingRules").doesNotExist())
                .andExpect(jsonPath("$.chunks").doesNotExist());
        assertNothingSaved();
    }

    @Test
    @DisplayName("범위가 넓은 질문은 NEEDS_CLARIFICATION과 구체화 안내를 반환한다")
    void search_success_broadQuestionNeedsClarification() throws Exception {
        // given
        Fixture fixture = saveFixture(true);

        // when
        ResultActions result = perform(
                fixture.workspaceId(),
                fixture.memberId(),
                "우리 프로젝트 어떻게 진행되고 있어?"
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.fallbackAnswer").value(org.hamcrest.Matchers.containsString("범위가 넓어요")));
        assertNothingSaved();
    }

    @Test
    @DisplayName("공개된 문서가 없으면 409 CHAT_DOCUMENTS_NOT_READY를 반환한다")
    void search_failure_documentsNotReady() throws Exception {
        // given
        Fixture fixture = saveFixture(false);

        // when
        ResultActions result = perform(
                fixture.workspaceId(),
                fixture.memberId(),
                QUESTION
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_DOCUMENTS_NOT_READY"));
        assertNothingSaved();
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 403 WORKSPACE_ACCESS_DENIED를 반환하고 DB를 바꾸지 않는다")
    void search_failure_notMember() throws Exception {
        // given
        Fixture fixture = saveFixture(true);
        indexChunks(
                fixture,
                List.of(1),
                QUESTION
        );

        // when
        ResultActions result = perform(
                fixture.workspaceId(),
                fixture.outsiderId(),
                QUESTION
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
        assertNothingSaved();
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스면 404 WORKSPACE_NOT_FOUND를 반환한다")
    void search_failure_workspaceNotFound() throws Exception {
        // given
        Fixture fixture = saveFixture(true);

        // when
        ResultActions result = perform(
                fixture.workspaceId() + 1000,
                fixture.memberId(),
                QUESTION
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("질문이 비어 있으면 400 VALIDATION_ERROR를 반환한다")
    void search_failure_blankContent() throws Exception {
        // given
        Fixture fixture = saveFixture(true);

        // when
        ResultActions result = perform(
                fixture.workspaceId(),
                fixture.memberId(),
                " "
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Bearer 토큰이 없으면 401을 반환한다")
    void search_failure_unauthenticated() throws Exception {
        // given
        Fixture fixture = saveFixture(true);

        // when
        ResultActions result = mockMvc.perform(
                post(
                        SEARCH_PATH,
                        fixture.workspaceId()
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + QUESTION + "\"}")
        );

        // then
        result.andExpect(status().isUnauthorized());
    }

    private ResultActions perform(
            long workspaceId,
            long memberId,
            String content
    ) throws Exception {
        return mockMvc.perform(
                post(
                        SEARCH_PATH,
                        workspaceId
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(memberId)
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}")
        );
    }

    private void assertNothingSaved() {
        assertThat(count("chat_sessions")).isZero();
        assertThat(count("chat_messages")).isZero();
        assertThat(count("search_references")).isZero();
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    /** 페이지별 청크 수만큼 질문과 겹치는 본문을 색인한다. fake 임베딩은 토큰 해시 기반이라 같은 문장이 가장 가깝다 */
    private void indexChunks(
            Fixture fixture,
            List<Integer> chunkCountsPerPage,
            String baseContent
    ) {
        List<SearchIndexedChunk> chunks = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        for (int pageOffset = 0; pageOffset < chunkCountsPerPage.size(); pageOffset++) {
            for (int chunkIndex = 0; chunkIndex < chunkCountsPerPage.get(pageOffset); chunkIndex++) {
                contents.add(baseContent + " 상세 " + pageOffset + "-" + chunkIndex);
            }
        }
        List<double[]> embeddings = embeddingClient.embed(
                contents,
                EmbeddingTask.DOCUMENT
        );
        int contentIndex = 0;
        for (int pageOffset = 0; pageOffset < chunkCountsPerPage.size(); pageOffset++) {
            for (int chunkIndex = 0; chunkIndex < chunkCountsPerPage.get(pageOffset); chunkIndex++) {
                chunks.add(
                        SearchIndexedChunk.of(
                                fixture.pageIds()
                                        .get(pageOffset),
                                fixture.importRunId(),
                                chunkIndex,
                                contents.get(contentIndex),
                                embeddings.get(contentIndex)
                        )
                );
                contentIndex++;
            }
        }
        searchChunkRepository.replace(
                fixture.workspaceId(),
                fixture.importRunId(),
                chunks
        );
    }

    private Fixture saveFixture(boolean published) {
        long memberId = saveMember("search-member");
        long outsiderId = saveMember("search-outsider");
        long workspaceId = jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES ('탐색 팀', :createdAt)
                RETURNING id
                """)
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .query(Long.class)
                .single();
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );
        long connectionId = jdbcClient.sql("""
                INSERT INTO content_source_connections (
                    workspace_id, provider, access_credential_ciphertext,
                    external_source_id, provider_connection_id, authorization_owner_type,
                    authorizing_member_id, created_at, updated_at
                ) VALUES (
                    :workspaceId, 'NOTION', 'test-ciphertext',
                    'notion-workspace', 'notion-connection', 'WORKSPACE',
                    :memberId, :createdAt, :createdAt
                )
                RETURNING id
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
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .query(Long.class)
                .single();
        long importRunId = jdbcClient.sql("""
                INSERT INTO content_import_runs (
                    workspace_id, content_source_connection_id, requested_by_member_id,
                    status, total_page_count, processed_page_count,
                    started_at, completed_at, created_at
                ) VALUES (
                    :workspaceId, :connectionId, :memberId,
                    'COMPLETED', 3, 3,
                    :createdAt, :completedAt, :createdAt
                )
                RETURNING id
                """)
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
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .param(
                        "completedAt",
                        CREATED_AT_OFFSET.plusSeconds(1)
                )
                .query(Long.class)
                .single();
        List<Long> pageIds = new ArrayList<>();
        for (int position = 0; position < 3; position++) {
            pageIds.add(
                    jdbcClient.sql("""
                            INSERT INTO imported_pages (
                                workspace_id, import_run_id, external_page_id, title,
                                markdown_content, position, source_url, created_at, updated_at
                            ) VALUES (
                                :workspaceId, :importRunId, :externalPageId, :title,
                                :markdown, :position, :sourceUrl, :createdAt, :updatedAt
                            )
                            RETURNING id
                            """)
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
                                    "notion-page-" + position
                            )
                            .param(
                                    "title",
                                    "문서 " + position
                            )
                            .param(
                                    "markdown",
                                    "문서 " + position + " 본문"
                            )
                            .param(
                                    "position",
                                    position
                            )
                            .param(
                                    "sourceUrl",
                                    "https://notion.test/page-" + position
                            )
                            .param(
                                    "createdAt",
                                    CREATED_AT_OFFSET
                            )
                            .param(
                                    "updatedAt",
                                    CREATED_AT_OFFSET.plusSeconds(3600)
                            )
                            .query(Long.class)
                            .single()
            );
        }
        if (published) {
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
                            importRunId
                    )
                    .param(
                            "publishedAt",
                            CREATED_AT_OFFSET.plusSeconds(2)
                    )
                    .update();
        }
        return new Fixture(
                memberId,
                outsiderId,
                workspaceId,
                importRunId,
                List.copyOf(pageIds)
        );
    }

    private long saveMember(String nickname) {
        return jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES (:nickname, NULL)
                RETURNING id
                """)
                .param(
                        "nickname",
                        nickname
                )
                .query(Long.class)
                .single();
    }

    private void saveWorkspaceMember(
            long workspaceId,
            long memberId,
            String role
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, :role, :joinedAt)
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
                        "role",
                        role
                )
                .param(
                        "joinedAt",
                        CREATED_AT_OFFSET
                )
                .update();
    }

    /** 인증 자격증명은 `Authorization: Bearer` 하나뿐이다(기획서 5.1) */
    private String bearerToken(long memberId) {
        return "Bearer " + authTokenProvider.issue(
                AuthenticatedMember.of(
                        memberId,
                        "search-member",
                        null
                )
        );
    }

    private record Fixture(
            long memberId,
            long outsiderId,
            long workspaceId,
            long importRunId,
            List<Long> pageIds
    ) {
    }
}
