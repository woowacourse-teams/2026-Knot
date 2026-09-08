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

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChatSearchAcceptanceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T00:00:00Z");
    private static final OffsetDateTime CREATED_AT_OFFSET = CREATED_AT.atOffset(ZoneOffset.UTC);
    private static final String QUESTION = "PostgreSQL을 왜 사용했나요?";
    private static final String SEARCH_PATH = "/api/v1/conversations/{sessionId}/search";

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;
    private final SearchChunkRepository searchChunkRepository;
    private final DocumentEmbeddingClient embeddingClient;

    ChatSearchAcceptanceTest(
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
    @DisplayName("근거를 찾으면 USER 메시지를 저장하고 같은 페이지를 포함한 청크 상위 8개와 규칙 문장을 반환한다")
    void search_success_readyReturnsTopEightChunks() throws Exception {
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
                fixture.sessionId(),
                fixture.ownerId(),
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
                .andExpect(jsonPath("$.userMessageId").isNumber())
                .andExpect(jsonPath("$.groundingRules").value(org.hamcrest.Matchers.containsString("근거 문서")))
                .andExpect(jsonPath("$.assistantMessageId").doesNotExist())
                .andExpect(jsonPath("$.fallbackAnswer").doesNotExist())
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
        long userMessageId = ((Number) JsonPath.read(
                body,
                "$.userMessageId"
        )).longValue();
        assertThat(
                jdbcClient.sql("""
                        SELECT role || ':' || generated_by || ':' || content
                        FROM chat_messages
                        WHERE session_id = :sessionId
                        ORDER BY created_at, id
                        """)
                        .param(
                                "sessionId",
                                fixture.sessionId()
                        )
                        .query(String.class)
                        .list()
        ).containsExactly("USER:SERVER:" + QUESTION);
        assertThat(
                jdbcClient.sql("SELECT id FROM chat_messages WHERE session_id = :sessionId")
                        .param(
                                "sessionId",
                                fixture.sessionId()
                        )
                        .query(Long.class)
                        .single()
        ).isEqualTo(userMessageId);
        assertThat(singleLastMessageAt(fixture.sessionId())).isAfter(CREATED_AT_OFFSET);
    }

    @Test
    @DisplayName("관련 문서가 없으면 USER와 안내 ASSISTANT를 저장하고 NO_RESULT를 반환한다")
    void search_success_noResultSavesFallbackAnswer() throws Exception {
        // given
        Fixture fixture = saveFixture(true);
        indexChunks(
                fixture,
                List.of(1),
                "점심 메뉴 추천 목록"
        );

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                "Redis 도입 근거"
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_RESULT"))
                .andExpect(jsonPath("$.userMessageId").isNumber())
                .andExpect(jsonPath("$.assistantMessageId").isNumber())
                .andExpect(jsonPath("$.fallbackAnswer").value(org.hamcrest.Matchers.containsString("찾지 못했습니다")))
                .andExpect(jsonPath("$.groundingRules").doesNotExist())
                .andExpect(jsonPath("$.chunks").doesNotExist());
        assertThat(
                jdbcClient.sql("""
                        SELECT role || ':' || generated_by
                        FROM chat_messages
                        WHERE session_id = :sessionId
                        ORDER BY created_at, id
                        """)
                        .param(
                                "sessionId",
                                fixture.sessionId()
                        )
                        .query(String.class)
                        .list()
        ).containsExactly(
                "USER:SERVER",
                "ASSISTANT:SERVER"
        );
        assertThat(countReferences(fixture.sessionId())).isZero();
    }

    @Test
    @DisplayName("범위가 넓은 질문은 구체화 안내를 저장하고 NEEDS_CLARIFICATION을 반환한다")
    void search_success_broadQuestionNeedsClarification() throws Exception {
        // given
        Fixture fixture = saveFixture(true);

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                "우리 프로젝트 어떻게 진행되고 있어?"
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.assistantMessageId").isNumber())
                .andExpect(jsonPath("$.fallbackAnswer").value(org.hamcrest.Matchers.containsString("범위가 넓어요")));
        assertThat(countChatMessages(fixture.sessionId())).isEqualTo(2);
    }

    @Test
    @DisplayName("답변 없는 USER 메시지가 남아 있으면 409로 거절하고 아무것도 저장하지 않는다")
    void search_failure_turnInProgress() throws Exception {
        // given
        Fixture fixture = saveFixture(true);
        saveUserMessage(
                fixture.sessionId(),
                OffsetDateTime.now(ZoneOffset.UTC)
                        .minusMinutes(1)
        );

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                QUESTION
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_TURN_IN_PROGRESS"))
                .andExpect(jsonPath("$.message").value("이전 질문의 답변이 아직 진행 중입니다"));
        assertThat(countChatMessages(fixture.sessionId())).isEqualTo(1);
    }

    @Test
    @DisplayName("timeout이 지난 미완 턴은 진행 중으로 보지 않고 새 검색을 받는다")
    void search_success_staleTurnIsAccepted() throws Exception {
        // given
        Fixture fixture = saveFixture(true);
        saveUserMessage(
                fixture.sessionId(),
                OffsetDateTime.now(ZoneOffset.UTC)
                        .minusMinutes(10)
        );
        indexChunks(
                fixture,
                List.of(1),
                QUESTION
        );

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                QUESTION
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        assertThat(countChatMessages(fixture.sessionId())).isEqualTo(2);
    }

    @Test
    @DisplayName("공개된 문서가 없으면 409로 거절하고 USER 메시지를 저장하지 않는다")
    void search_failure_documentsNotReady() throws Exception {
        // given
        Fixture fixture = saveFixture(false);

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                QUESTION
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_DOCUMENTS_NOT_READY"));
        assertThat(countChatMessages(fixture.sessionId())).isZero();
    }

    @Test
    @DisplayName("세션 소유자가 아니면 403을 반환한다")
    void search_failure_accessDenied() throws Exception {
        // given
        Fixture fixture = saveFixture(true);

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.otherMemberId(),
                QUESTION
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_ACCESS_DENIED"));
        assertThat(countChatMessages(fixture.sessionId())).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 세션이면 404를 반환한다")
    void search_failure_sessionNotFound() throws Exception {
        // given
        Fixture fixture = saveFixture(true);

        // when
        ResultActions result = perform(
                fixture.sessionId() + 1000,
                fixture.ownerId(),
                QUESTION
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("질문이 비어 있으면 400을 반환한다")
    void search_failure_blankContent() throws Exception {
        // given
        Fixture fixture = saveFixture(true);

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                " "
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(countChatMessages(fixture.sessionId())).isZero();
    }

    private ResultActions perform(
            long sessionId,
            long memberId,
            String content
    ) throws Exception {
        return mockMvc.perform(
                post(
                        SEARCH_PATH,
                        sessionId
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(memberId)
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}")
        );
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
        long ownerId = saveMember("search-owner");
        long otherMemberId = saveMember("search-member");
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
                ownerId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                otherMemberId,
                "MEMBER"
        );
        long sessionId = jdbcClient.sql("""
                INSERT INTO chat_sessions (workspace_id, member_id, title, created_at, last_message_at)
                VALUES (:workspaceId, :memberId, '탐색', :createdAt, :createdAt)
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        ownerId
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .query(Long.class)
                .single();
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
                        ownerId
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
                        ownerId
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
                ownerId,
                otherMemberId,
                workspaceId,
                sessionId,
                importRunId,
                List.copyOf(pageIds)
        );
    }

    private void saveUserMessage(
            long sessionId,
            OffsetDateTime createdAt
    ) {
        jdbcClient.sql("""
                INSERT INTO chat_messages (session_id, role, content, created_at)
                VALUES (:sessionId, 'USER', '답변을 기다리는 질문', :createdAt)
                """)
                .param(
                        "sessionId",
                        sessionId
                )
                .param(
                        "createdAt",
                        createdAt
                )
                .update();
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

    private long countChatMessages(long sessionId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM chat_messages WHERE session_id = :sessionId")
                .param(
                        "sessionId",
                        sessionId
                )
                .query(Long.class)
                .single();
    }

    private long countReferences(long sessionId) {
        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM search_references reference
                JOIN chat_messages message ON message.id = reference.message_id
                WHERE message.session_id = :sessionId
                """)
                .param(
                        "sessionId",
                        sessionId
                )
                .query(Long.class)
                .single();
    }

    private OffsetDateTime singleLastMessageAt(long sessionId) {
        return jdbcClient.sql("SELECT last_message_at FROM chat_sessions WHERE id = :sessionId")
                .param(
                        "sessionId",
                        sessionId
                )
                .query(OffsetDateTime.class)
                .single();
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
            long ownerId,
            long otherMemberId,
            long workspaceId,
            long sessionId,
            long importRunId,
            List<Long> pageIds
    ) {
    }
}
