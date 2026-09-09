package com.knot.backend.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
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
 * 턴 저장 API(기획서 6.4, 로드맵 S2). CLI 에이전트가 만든 질문·답변·근거를 한 트랜잭션에 저장하고, 출처 조회가 청크 단위
 * 8건을 돌려주는지까지 본다.
 */
@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChatTurnAcceptanceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T00:00:00Z");
    private static final OffsetDateTime CREATED_AT_OFFSET = CREATED_AT.atOffset(ZoneOffset.UTC);
    private static final String QUESTION = "PostgreSQL을 왜 사용했나요?";
    private static final String ANSWER = "pgvector 확장을 쓰기 위해 PostgreSQL을 선택했습니다. [근거 문서 1]";
    private static final String TURNS_PATH = "/api/v1/conversations/{sessionId}/turns";
    private static final String SOURCES_PATH = "/api/v1/messages/{messageId}/sources";

    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;

    ChatTurnAcceptanceTest(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            JdbcClient jdbcClient
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.jdbcClient = jdbcClient;
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
    @DisplayName("질문·답변·근거를 한 번에 저장하고 출처 조회가 청크 단위 근거를 rank 순서로 돌려준다")
    void save_success_persistsTurnAndReferences() throws Exception {
        // given
        Fixture fixture = saveFixture();
        String body = requestBody(
                QUESTION,
                ANSWER,
                List.of(
                        reference(
                                fixture,
                                0,
                                2,
                                0.95
                        ),
                        reference(
                                fixture,
                                0,
                                0,
                                0.9
                        ),
                        reference(
                                fixture,
                                1,
                                1,
                                1.5
                        )
                )
        );

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                body
        );

        // then
        result.andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userMessageId").isNumber())
                .andExpect(jsonPath("$.messageId").isNumber());
        String response = result.andReturn()
                .getResponse()
                .getContentAsString();
        long userMessageId = ((Number) JsonPath.read(
                response,
                "$.userMessageId"
        )).longValue();
        long messageId = ((Number) JsonPath.read(
                response,
                "$.messageId"
        )).longValue();
        assertThat(messageId).isGreaterThan(userMessageId);
        assertThat(messageRows(fixture.sessionId())).containsExactly(
                "USER:SERVER:" + QUESTION,
                "ASSISTANT:CLIENT:" + ANSWER
        );
        assertThat(referenceRows(messageId)).containsExactly(
                "1:" + fixture.pageIds()
                        .get(0) + ":2:0.95",
                "2:" + fixture.pageIds()
                        .get(0) + ":0:0.9",
                "3:" + fixture.pageIds()
                        .get(1) + ":1:1"
        );
        assertThat(singleLastMessageAt(fixture.sessionId())).isAfter(CREATED_AT_OFFSET);

        mockMvc.perform(
                get(
                        SOURCES_PATH,
                        messageId
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(fixture.ownerId())
                )
        )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.searchReferences",
                                hasSize(3)
                        )
                )
                .andExpect(jsonPath("$.searchReferences[0].rank").value(1))
                .andExpect(jsonPath("$.searchReferences[0].chunkIndex").value(2))
                .andExpect(jsonPath("$.searchReferences[0].relevanceScore").value(0.95))
                .andExpect(jsonPath("$.searchReferences[0].notionPage.title").value("문서 0"))
                .andExpect(jsonPath("$.searchReferences[1].rank").value(2))
                .andExpect(jsonPath("$.searchReferences[1].chunkIndex").value(0))
                .andExpect(jsonPath("$.searchReferences[2].rank").value(3))
                .andExpect(jsonPath("$.searchReferences[2].chunkIndex").value(1))
                .andExpect(jsonPath("$.searchReferences[2].relevanceScore").value(1.0));
    }

    @Test
    @DisplayName("같은 페이지의 청크 8개를 근거로 받고 출처 조회가 8건을 돌려준다")
    void save_success_eightChunksOfOnePage() throws Exception {
        // given
        Fixture fixture = saveFixture();
        List<String> references = IntStream.range(
                0,
                8
        )
                .mapToObj(
                        chunkIndex -> reference(
                                fixture,
                                0,
                                chunkIndex,
                                0.9 - chunkIndex * 0.05
                        )
                )
                .toList();

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                requestBody(
                        QUESTION,
                        ANSWER,
                        references
                )
        );

        // then
        result.andExpect(status().isCreated());
        long messageId = ((Number) JsonPath.read(
                result.andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.messageId"
        )).longValue();
        assertThat(referenceRows(messageId)).hasSize(8);
        mockMvc.perform(
                get(
                        SOURCES_PATH,
                        messageId
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(fixture.ownerId())
                )
        )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.searchReferences",
                                hasSize(8)
                        )
                )
                .andExpect(jsonPath("$.searchReferences[7].rank").value(8))
                .andExpect(jsonPath("$.searchReferences[7].chunkIndex").value(7));
    }

    @Test
    @DisplayName("근거 없이도 질문과 답변을 저장한다")
    void save_success_withoutReferences() throws Exception {
        // given
        Fixture fixture = saveFixture();

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                requestBody(
                        QUESTION,
                        "관련 문서를 찾지 못했습니다.",
                        List.of()
                )
        );

        // then
        result.andExpect(status().isCreated());
        assertThat(countChatMessages(fixture.sessionId())).isEqualTo(2);
        assertThat(countReferences(fixture.sessionId())).isZero();
    }

    @Test
    @DisplayName("timeout이 지난 미완 USER 턴이 있어도 새 턴을 저장한다")
    void save_success_staleTurnIsAccepted() throws Exception {
        // given
        Fixture fixture = saveFixture();
        saveUserMessage(
                fixture.sessionId(),
                OffsetDateTime.now(ZoneOffset.UTC)
                        .minusMinutes(10)
        );

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                requestBody(
                        QUESTION,
                        ANSWER,
                        List.of()
                )
        );

        // then
        result.andExpect(status().isCreated());
        assertThat(countChatMessages(fixture.sessionId())).isEqualTo(3);
    }

    @Test
    @DisplayName("답변 없는 USER 메시지가 timeout 안이면 409로 거절하고 아무것도 저장하지 않는다")
    void save_failure_turnInProgress() throws Exception {
        // given
        Fixture fixture = saveFixture();
        saveUserMessage(
                fixture.sessionId(),
                OffsetDateTime.now(ZoneOffset.UTC)
                        .minusMinutes(1)
        );

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                requestBody(
                        QUESTION,
                        ANSWER,
                        List.of(
                                reference(
                                        fixture,
                                        0,
                                        0,
                                        0.9
                                )
                        )
                )
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_TURN_IN_PROGRESS"));
        assertThat(countChatMessages(fixture.sessionId())).isEqualTo(1);
        assertThat(countReferences(fixture.sessionId())).isZero();
    }

    @Test
    @DisplayName("다른 Workspace의 페이지를 근거로 보내면 400으로 거절하고 질문·답변도 저장하지 않는다")
    void save_failure_referenceOutsideWorkspace() throws Exception {
        // given
        Fixture fixture = saveFixture();
        ForeignPage foreignPage = saveForeignPage(fixture.ownerId());

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                requestBody(
                        QUESTION,
                        ANSWER,
                        List.of(
                                reference(
                                        fixture,
                                        0,
                                        0,
                                        0.9
                                ),
                                referenceJson(
                                        foreignPage.importRunId(),
                                        foreignPage.pageId(),
                                        0,
                                        0.8
                                )
                        )
                )
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHAT_TURN_REFERENCE_INVALID"));
        assertThat(countChatMessages(fixture.sessionId())).isZero();
        assertThat(countReferences(fixture.sessionId())).isZero();
        assertThat(singleLastMessageAt(fixture.sessionId())).isEqualTo(CREATED_AT_OFFSET);
    }

    @Test
    @DisplayName("같은 페이지의 같은 청크가 중복되면 400으로 거절하고 아무것도 저장하지 않는다")
    void save_failure_duplicateReference() throws Exception {
        // given
        Fixture fixture = saveFixture();

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                requestBody(
                        QUESTION,
                        ANSWER,
                        List.of(
                                reference(
                                        fixture,
                                        0,
                                        1,
                                        0.9
                                ),
                                reference(
                                        fixture,
                                        0,
                                        1,
                                        0.8
                                )
                        )
                )
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHAT_TURN_REFERENCE_INVALID"));
        assertThat(countChatMessages(fixture.sessionId())).isZero();
    }

    @Test
    @DisplayName("근거가 9개면 400 VALIDATION_ERROR로 거절한다")
    void save_failure_tooManyReferences() throws Exception {
        // given
        Fixture fixture = saveFixture();
        List<String> references = IntStream.range(
                0,
                9
        )
                .mapToObj(
                        chunkIndex -> reference(
                                fixture,
                                0,
                                chunkIndex,
                                0.9
                        )
                )
                .toList();

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                requestBody(
                        QUESTION,
                        ANSWER,
                        references
                )
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(countChatMessages(fixture.sessionId())).isZero();
    }

    @Test
    @DisplayName("질문이나 답변이 비어 있거나 근거 필드가 빠지면 400 VALIDATION_ERROR로 거절한다")
    void save_failure_invalidBody() throws Exception {
        // given
        Fixture fixture = saveFixture();

        // when
        ResultActions blankQuestion = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                requestBody(
                        " ",
                        ANSWER,
                        List.of()
                )
        );
        ResultActions blankAnswer = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                requestBody(
                        QUESTION,
                        "",
                        List.of()
                )
        );
        ResultActions missingChunkIndex = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                requestBody(
                        QUESTION,
                        ANSWER,
                        List.of(
                                "{\"importRunId\":" + fixture.importRunId() + ",\"importedPageId\":" + fixture.pageIds()
                                        .getFirst() + ",\"score\":0.9}"
                        )
                )
        );
        ResultActions missingReferences = perform(
                fixture.sessionId(),
                fixture.ownerId(),
                "{\"question\":\"" + QUESTION + "\",\"answer\":\"" + ANSWER + "\"}"
        );

        // then
        blankQuestion.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        blankAnswer.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        missingChunkIndex.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        missingReferences.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(countChatMessages(fixture.sessionId())).isZero();
    }

    @Test
    @DisplayName("세션 소유자가 아니면 403을 반환하고 아무것도 저장하지 않는다")
    void save_failure_accessDenied() throws Exception {
        // given
        Fixture fixture = saveFixture();

        // when
        ResultActions result = perform(
                fixture.sessionId(),
                fixture.otherMemberId(),
                requestBody(
                        QUESTION,
                        ANSWER,
                        List.of()
                )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_ACCESS_DENIED"));
        assertThat(countChatMessages(fixture.sessionId())).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 세션이면 404를 반환한다")
    void save_failure_sessionNotFound() throws Exception {
        // given
        Fixture fixture = saveFixture();

        // when
        ResultActions result = perform(
                fixture.sessionId() + 1000,
                fixture.ownerId(),
                requestBody(
                        QUESTION,
                        ANSWER,
                        List.of()
                )
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환한다")
    void save_failure_unauthenticated() throws Exception {
        // given
        Fixture fixture = saveFixture();

        // when
        ResultActions result = mockMvc.perform(
                post(
                        TURNS_PATH,
                        fixture.sessionId()
                ).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                requestBody(
                                        QUESTION,
                                        ANSWER,
                                        List.of()
                                )
                        )
        );

        // then
        result.andExpect(status().isUnauthorized());
        assertThat(countChatMessages(fixture.sessionId())).isZero();
    }

    private ResultActions perform(
            long sessionId,
            long memberId,
            String body
    ) throws Exception {
        return mockMvc.perform(
                post(
                        TURNS_PATH,
                        sessionId
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        bearerToken(memberId)
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );
    }

    private static String requestBody(
            String question,
            String answer,
            List<String> referenceJsons
    ) {
        return "{\"question\":\"" + question + "\",\"answer\":\"" + answer + "\",\"references\":["
                + String.join(
                        ",",
                        referenceJsons
                ) + "]}";
    }

    private static String reference(
            Fixture fixture,
            int pageOffset,
            int chunkIndex,
            double score
    ) {
        return referenceJson(
                fixture.importRunId(),
                fixture.pageIds()
                        .get(pageOffset),
                chunkIndex,
                score
        );
    }

    private static String referenceJson(
            long importRunId,
            long importedPageId,
            int chunkIndex,
            double score
    ) {
        return "{\"importRunId\":" + importRunId + ",\"importedPageId\":" + importedPageId + ",\"chunkIndex\":"
                + chunkIndex + ",\"score\":" + score + "}";
    }

    private Fixture saveFixture() {
        long ownerId = saveMember("turn-owner");
        long otherMemberId = saveMember("turn-member");
        long workspaceId = saveWorkspace("탐색 팀");
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
        long importRunId = saveImportRun(
                workspaceId,
                ownerId
        );
        List<Long> pageIds = new ArrayList<>();
        for (int position = 0; position < 2; position++) {
            pageIds.add(
                    savePage(
                            workspaceId,
                            importRunId,
                            position
                    )
            );
        }
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
        return new Fixture(
                ownerId,
                otherMemberId,
                workspaceId,
                sessionId,
                importRunId,
                List.copyOf(pageIds)
        );
    }

    /** 세션 소유자가 멤버인 다른 Workspace의 페이지. Workspace JOIN 검증이 이것을 걸러야 한다(로드맵 Q24). */
    private ForeignPage saveForeignPage(long memberId) {
        long workspaceId = saveWorkspace("다른 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );
        long importRunId = saveImportRun(
                workspaceId,
                memberId
        );
        long pageId = savePage(
                workspaceId,
                importRunId,
                0
        );
        return new ForeignPage(
                importRunId,
                pageId
        );
    }

    private long saveWorkspace(String name) {
        return jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES (:name, :createdAt)
                RETURNING id
                """)
                .param(
                        "name",
                        name
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .query(Long.class)
                .single();
    }

    private long saveImportRun(
            long workspaceId,
            long memberId
    ) {
        long connectionId = jdbcClient.sql("""
                INSERT INTO content_source_connections (
                    workspace_id, provider, access_credential_ciphertext,
                    external_source_id, provider_connection_id, authorization_owner_type,
                    authorizing_member_id, created_at, updated_at
                ) VALUES (
                    :workspaceId, 'NOTION', 'test-ciphertext',
                    :externalSourceId, :providerConnectionId, 'WORKSPACE',
                    :memberId, :createdAt, :createdAt
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "externalSourceId",
                        "notion-workspace-" + workspaceId
                )
                .param(
                        "providerConnectionId",
                        "notion-connection-" + workspaceId
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
        return jdbcClient.sql("""
                INSERT INTO content_import_runs (
                    workspace_id, content_source_connection_id, requested_by_member_id,
                    status, total_page_count, processed_page_count,
                    started_at, completed_at, created_at
                ) VALUES (
                    :workspaceId, :connectionId, :memberId,
                    'COMPLETED', 2, 2,
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
    }

    private long savePage(
            long workspaceId,
            long importRunId,
            int position
    ) {
        return jdbcClient.sql("""
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
                        "notion-page-" + workspaceId + "-" + position
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
                        "https://notion.test/page-" + workspaceId + "-" + position
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
                .single();
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

    private List<String> messageRows(long sessionId) {
        return jdbcClient.sql("""
                SELECT role || ':' || generated_by || ':' || content
                FROM chat_messages
                WHERE session_id = :sessionId
                ORDER BY created_at, id
                """)
                .param(
                        "sessionId",
                        sessionId
                )
                .query(String.class)
                .list();
    }

    private List<String> referenceRows(long messageId) {
        return jdbcClient.sql("""
                SELECT reference_rank || ':' || imported_page_id || ':' || chunk_index || ':' || relevance_score
                FROM search_references
                WHERE message_id = :messageId
                ORDER BY reference_rank
                """)
                .param(
                        "messageId",
                        messageId
                )
                .query(String.class)
                .list();
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
                        "turn-member",
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

    private record ForeignPage(
            long importRunId,
            long pageId
    ) {
    }
}
