package com.knot.backend.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.chat.domain.ChatFeedback;
import com.knot.backend.chat.domain.ChatFeedbackRepository;
import com.knot.backend.chat.domain.ChatFeedbackResult;
import com.knot.backend.chat.domain.ChatMessage;
import com.knot.backend.chat.domain.ChatMessageRepository;
import com.knot.backend.chat.domain.ChatMessageRole;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import com.knot.backend.chat.domain.LlmUsage;
import com.knot.backend.chat.application.ChatMessagePersistenceService;
import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = AutowireMode.ALL)
class ChatRepositoryIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T00:00:00Z");
    private static final OffsetDateTime CREATED_AT_OFFSET = CREATED_AT.atOffset(ZoneOffset.UTC);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatFeedbackRepository chatFeedbackRepository;
    private final ChatMessagePersistenceService chatMessagePersistenceService;
    private final JdbcClient jdbcClient;
    private final EntityManager entityManager;

    ChatRepositoryIntegrationTest(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatFeedbackRepository chatFeedbackRepository,
            ChatMessagePersistenceService chatMessagePersistenceService,
            JdbcClient jdbcClient,
            EntityManager entityManager
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatFeedbackRepository = chatFeedbackRepository;
        this.chatMessagePersistenceService = chatMessagePersistenceService;
        this.jdbcClient = jdbcClient;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void clearChatTables() {
        jdbcClient.sql("TRUNCATE TABLE search_references, chat_feedback, chat_messages, chat_sessions RESTART IDENTITY")
                .update();
    }

    @Test
    @DisplayName("채팅 세션과 메시지를 저장하고 조회한다")
    @Transactional
    void saveAndFind_success() {
        // given
        long[] workspaceMemberIds = saveWorkspaceMember(
                1L,
                1L
        );
        ChatSession chatSession = chatSessionRepository.save(
                ChatSession.create(
                        workspaceMemberIds[0],
                        workspaceMemberIds[1],
                        null,
                        CREATED_AT
                )
        );
        ChatMessage userMessage = chatMessageRepository.save(
                ChatMessage.create(
                        chatSession.getId(),
                        ChatMessageRole.USER,
                        "질문",
                        CREATED_AT.plusSeconds(1)
                )
        );
        chatSession.updateLastMessageAt(userMessage.getCreatedAt());
        chatSessionRepository.save(chatSession);

        // when
        List<ChatMessage> messages = chatMessageRepository.findAllBySessionId(chatSession.getId());

        // then
        assertThat(chatSessionRepository.findById(chatSession.getId())).get()
                .extracting(
                        ChatSession::getTitle,
                        ChatSession::getLastMessageAt
                )
                .containsExactly(
                        ChatSession.DEFAULT_TITLE,
                        userMessage.getCreatedAt()
                );
        assertThat(messages).extracting(
                ChatMessage::getRole,
                ChatMessage::getContent
        )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                ChatMessageRole.USER,
                                "질문"
                        )
                );
    }

    @Test
    @DisplayName("같은 메시지와 멤버의 피드백은 중복 저장할 수 없다")
    @Transactional
    void save_failure_duplicateFeedback() {
        // given
        long[] workspaceMemberIds = saveWorkspaceMember(
                2L,
                2L
        );
        ChatSession chatSession = chatSessionRepository.save(
                ChatSession.create(
                        workspaceMemberIds[0],
                        workspaceMemberIds[1],
                        "피드백",
                        CREATED_AT
                )
        );
        ChatMessage chatMessage = chatMessageRepository.save(
                ChatMessage.create(
                        chatSession.getId(),
                        ChatMessageRole.ASSISTANT,
                        "답변",
                        CREATED_AT
                )
        );
        chatFeedbackRepository.save(
                ChatFeedback.create(
                        chatMessage.getId(),
                        workspaceMemberIds[1],
                        ChatFeedbackResult.LIKE,
                        CREATED_AT
                )
        );
        ChatFeedback duplicate = ChatFeedback.create(
                chatMessage.getId(),
                workspaceMemberIds[1],
                ChatFeedbackResult.DISLIKE,
                CREATED_AT
        );

        // when
        ThrowingCallable action = () -> {
            chatFeedbackRepository.save(duplicate);
            entityManager.flush();
        };

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("workspace member가 아닌 세션 소유자는 데이터베이스에 저장할 수 없다")
    @Transactional
    void save_failure_missingWorkspaceMember() {
        // given
        ChatSession chatSession = ChatSession.create(
                999L,
                999L,
                "권한",
                CREATED_AT
        );

        // when
        ThrowingCallable action = () -> {
            chatSessionRepository.save(chatSession);
            entityManager.flush();
        };

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 workspace의 문서 근거를 저장하면 assistant와 세션 변경을 함께 롤백한다")
    void saveAssistantWithReferences_failure_crossWorkspaceRollsBack() {
        // given
        long[] firstWorkspaceMember = saveWorkspaceMember(
                1L,
                1L
        );
        long[] secondWorkspaceMember = saveWorkspaceMember(
                2L,
                2L
        );
        long[] secondPage = savePublishedPage(
                secondWorkspaceMember[0],
                secondWorkspaceMember[1]
        );
        ChatSession chatSession = chatSessionRepository.save(
                ChatSession.create(
                        firstWorkspaceMember[0],
                        firstWorkspaceMember[1],
                        "근거",
                        CREATED_AT
                )
        );
        SearchChunk crossWorkspaceReference = SearchChunk.retrieved(
                secondWorkspaceMember[0],
                secondPage[0],
                secondPage[1],
                0,
                "다른 팀 문서",
                "https://notion.test/other",
                CREATED_AT,
                "다른 팀 내용",
                0.9
        );

        // when
        ThrowingCallable action = () -> chatMessagePersistenceService.saveAssistantWithReferences(
                chatSession.getId(),
                "답변",
                CREATED_AT.plusSeconds(1),
                List.of(crossWorkspaceReference),
                null
        );

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode()).isEqualTo(SearchErrorCode.SEARCH_REFERENCE_FAILED)
        );
        assertThat(chatMessageRepository.findAllBySessionId(chatSession.getId())).isEmpty();
        assertThat(chatSessionRepository.findById(chatSession.getId())).get()
                .extracting(ChatSession::getLastMessageAt)
                .isEqualTo(CREATED_AT);
        assertThat(
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM search_references reference
                        JOIN chat_messages message ON message.id = reference.message_id
                        WHERE message.session_id = :sessionId
                        """)
                        .param(
                                "sessionId",
                                chatSession.getId()
                        )
                        .query(Long.class)
                        .single()
        ).isZero();
    }

    @Test
    @DisplayName("같은 페이지의 청크 여러 개를 chunk_index와 함께 rank 순서로 저장한다")
    void saveAssistantWithReferences_success_storesChunkIndexAllowingSamePage() {
        // given
        long[] workspaceMember = saveWorkspaceMember(
                1L,
                1L
        );
        long[] page = savePublishedPage(
                workspaceMember[0],
                workspaceMember[1]
        );
        ChatSession chatSession = chatSessionRepository.save(
                ChatSession.create(
                        workspaceMember[0],
                        workspaceMember[1],
                        "근거",
                        CREATED_AT
                )
        );

        // when
        ChatMessage assistantMessage = chatMessagePersistenceService.saveAssistantWithReferences(
                chatSession.getId(),
                "답변",
                CREATED_AT.plusSeconds(1),
                List.of(
                        chunk(
                                workspaceMember[0],
                                page,
                                3,
                                0.95
                        ),
                        chunk(
                                workspaceMember[0],
                                page,
                                0,
                                0.8
                        )
                ),
                null
        );

        // then
        assertThat(
                jdbcClient.sql("""
                        SELECT reference_rank || ':' || chunk_index || ':' || relevance_score
                        FROM search_references
                        WHERE message_id = :messageId
                        ORDER BY reference_rank
                        """)
                        .param(
                                "messageId",
                                assistantMessage.getId()
                        )
                        .query(String.class)
                        .list()
        ).containsExactly(
                "1:3:0.95",
                "2:0:0.8"
        );
        assertThat(
                jdbcClient.sql("SELECT generated_by FROM chat_messages WHERE id = :messageId")
                        .param(
                                "messageId",
                                assistantMessage.getId()
                        )
                        .query(String.class)
                        .single()
        ).isEqualTo("SERVER");
    }

    @Test
    @DisplayName("서버가 모델을 불러 만든 답변은 토큰 사용량을 같은 행에 저장한다")
    void saveAssistantWithReferences_success_storesLlmUsage() {
        // given
        long[] workspaceMember = saveWorkspaceMember(
                1L,
                1L
        );
        ChatSession chatSession = chatSessionRepository.save(
                ChatSession.create(
                        workspaceMember[0],
                        workspaceMember[1],
                        "사용량",
                        CREATED_AT
                )
        );

        // when
        ChatMessage assistantMessage = chatMessagePersistenceService.saveAssistantWithReferences(
                chatSession.getId(),
                "답변",
                CREATED_AT.plusSeconds(1),
                List.of(),
                LlmUsage.of(
                        "claude-opus-5",
                        25L,
                        12L,
                        9L,
                        3L
                )
        );

        // then
        assertThat(
                jdbcClient.sql("""
                        SELECT llm_model || ':' || input_tokens || ':' || output_tokens || ':'
                               || cache_read_input_tokens || ':' || cache_creation_input_tokens
                        FROM chat_messages
                        WHERE id = :messageId
                        """)
                        .param(
                                "messageId",
                                assistantMessage.getId()
                        )
                        .query(String.class)
                        .single()
        ).isEqualTo("claude-opus-5:25:12:9:3");
        entityManager.clear();
        assertThat(
                chatMessageRepository.findById(assistantMessage.getId())
                        .orElseThrow()
                        .getUsage()
                        .getOutputTokens()
        ).isEqualTo(12L);
    }

    @Test
    @DisplayName("사용량 없이 저장한 답변은 사용량 컬럼이 전부 비어 있다")
    void saveAssistantWithReferences_success_withoutUsageLeavesColumnsNull() {
        // given
        long[] workspaceMember = saveWorkspaceMember(
                1L,
                1L
        );
        ChatSession chatSession = chatSessionRepository.save(
                ChatSession.create(
                        workspaceMember[0],
                        workspaceMember[1],
                        "사용량 없음",
                        CREATED_AT
                )
        );

        // when
        ChatMessage assistantMessage = chatMessagePersistenceService.saveAssistantWithReferences(
                chatSession.getId(),
                "안내 문구",
                CREATED_AT.plusSeconds(1),
                List.of(),
                null
        );

        // then
        assertThat(
                jdbcClient.sql("""
                        SELECT count(*)
                        FROM chat_messages
                        WHERE id = :messageId
                          AND llm_model IS NULL
                          AND input_tokens IS NULL
                          AND output_tokens IS NULL
                          AND cache_read_input_tokens IS NULL
                          AND cache_creation_input_tokens IS NULL
                        """)
                        .param(
                                "messageId",
                                assistantMessage.getId()
                        )
                        .query(Long.class)
                        .single()
        ).isEqualTo(1L);
        entityManager.clear();
        assertThat(
                chatMessageRepository.findById(assistantMessage.getId())
                        .orElseThrow()
                        .getUsage()
        ).isNull();
    }

    @Test
    @DisplayName("근거가 8개를 넘으면 rank 제약에 걸려 assistant 저장을 롤백한다")
    void saveAssistantWithReferences_failure_moreThanEightReferencesRollsBack() {
        // given
        long[] workspaceMember = saveWorkspaceMember(
                1L,
                1L
        );
        long[] page = savePublishedPage(
                workspaceMember[0],
                workspaceMember[1]
        );
        ChatSession chatSession = chatSessionRepository.save(
                ChatSession.create(
                        workspaceMember[0],
                        workspaceMember[1],
                        "근거",
                        CREATED_AT
                )
        );
        List<SearchChunk> nineReferences = java.util.stream.IntStream.range(
                0,
                9
        )
                .mapToObj(
                        index -> chunk(
                                workspaceMember[0],
                                page,
                                index,
                                0.9 - index * 0.05
                        )
                )
                .toList();

        // when
        ThrowingCallable action = () -> chatMessagePersistenceService.saveAssistantWithReferences(
                chatSession.getId(),
                "답변",
                CREATED_AT.plusSeconds(1),
                nineReferences,
                null
        );

        // then
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(chatMessageRepository.findAllBySessionId(chatSession.getId())).isEmpty();
    }

    @Test
    @DisplayName("근거 없음 턴은 USER 질문과 서버 안내 답변을 한 트랜잭션에 SERVER 생성으로 저장한다")
    void saveFallbackTurn_success_persistsUserAndAssistant() {
        // given
        long[] workspaceMember = saveWorkspaceMember(
                1L,
                1L
        );
        ChatSession chatSession = chatSessionRepository.save(
                ChatSession.create(
                        workspaceMember[0],
                        workspaceMember[1],
                        "안내",
                        CREATED_AT
                )
        );

        // when
        var turn = chatMessagePersistenceService.saveFallbackTurn(
                chatSession.getId(),
                "무관한 질문",
                "관련된 정보를 찾지 못했습니다.",
                CREATED_AT.plusSeconds(5)
        );

        // then
        List<ChatMessage> messages = chatMessageRepository.findAllBySessionId(chatSession.getId());
        assertThat(messages).extracting(
                ChatMessage::getId,
                ChatMessage::getRole,
                ChatMessage::getContent,
                ChatMessage::getGeneratedBy
        )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                turn.userMessage()
                                        .getId(),
                                ChatMessageRole.USER,
                                "무관한 질문",
                                com.knot.backend.chat.domain.ChatMessageGeneratedBy.SERVER
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                turn.assistantMessage()
                                        .getId(),
                                ChatMessageRole.ASSISTANT,
                                "관련된 정보를 찾지 못했습니다.",
                                com.knot.backend.chat.domain.ChatMessageGeneratedBy.SERVER
                        )
                );
        assertThat(chatSessionRepository.findById(chatSession.getId())).get()
                .extracting(ChatSession::getLastMessageAt)
                .isEqualTo(CREATED_AT.plusSeconds(5));
    }

    private SearchChunk chunk(
            long workspaceId,
            long[] page,
            int chunkIndex,
            double score
    ) {
        return SearchChunk.retrieved(
                workspaceId,
                page[0],
                page[1],
                chunkIndex,
                "문서",
                "https://notion.test/page",
                CREATED_AT,
                "청크 " + chunkIndex,
                score
        );
    }

    private long[] saveWorkspaceMember(
            long workspaceNumber,
            long memberNumber
    ) {
        long memberId = jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES (:nickname, NULL)
                RETURNING id
                """)
                .param(
                        "nickname",
                        "chat-member-" + memberNumber
                )
                .query(Long.class)
                .single();
        long workspaceId = jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES (:name, :createdAt)
                RETURNING id
                """)
                .param(
                        "name",
                        "채팅 팀 " + workspaceNumber
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .query(Long.class)
                .single();
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
                        CREATED_AT_OFFSET
                )
                .update();
        return new long[]{workspaceId, memberId};
    }

    private long[] savePublishedPage(
            long workspaceId,
            long memberId
    ) {
        long connectionId = jdbcClient.sql("""
                INSERT INTO content_source_connections (
                    workspace_id, provider, access_credential_ciphertext,
                    external_source_id, provider_connection_id, authorization_owner_type,
                    authorizing_member_id, created_at, updated_at
                ) VALUES (
                    :workspaceId, 'NOTION', 'ciphertext',
                    :externalSourceId, :providerConnectionId, 'WORKSPACE',
                    :memberId, :createdAt, :updatedAt
                )
                RETURNING id
                """)
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
                        CREATED_AT_OFFSET
                )
                .param(
                        "updatedAt",
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
                    'COMPLETED', 1, 1,
                    :startedAt, :completedAt, :createdAt
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
                        "startedAt",
                        CREATED_AT_OFFSET
                )
                .param(
                        "completedAt",
                        CREATED_AT_OFFSET.plusSeconds(1)
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .query(Long.class)
                .single();
        long pageId = jdbcClient.sql("""
                INSERT INTO imported_pages (
                    workspace_id, import_run_id, external_page_id, title,
                    markdown_content, position, source_url, created_at, updated_at
                ) VALUES (
                    :workspaceId, :importRunId, :externalPageId, :title,
                    :markdownContent, 0, :sourceUrl, :createdAt, :updatedAt
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
                        "page-" + workspaceId
                )
                .param(
                        "title",
                        "팀 문서 " + workspaceId
                )
                .param(
                        "markdownContent",
                        "팀 문서 본문"
                )
                .param(
                        "sourceUrl",
                        "https://notion.test/" + workspaceId
                )
                .param(
                        "createdAt",
                        CREATED_AT_OFFSET
                )
                .param(
                        "updatedAt",
                        CREATED_AT_OFFSET
                )
                .query(Long.class)
                .single();
        jdbcClient.sql("""
                INSERT INTO imported_page_publications (
                    workspace_id, published_import_run_id, published_at
                ) VALUES (:workspaceId, :importRunId, :publishedAt)
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
        return new long[]{pageId, importRunId};
    }
}
