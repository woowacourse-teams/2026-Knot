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
    private final JdbcClient jdbcClient;
    private final EntityManager entityManager;

    ChatRepositoryIntegrationTest(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatFeedbackRepository chatFeedbackRepository,
            JdbcClient jdbcClient,
            EntityManager entityManager
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatFeedbackRepository = chatFeedbackRepository;
        this.jdbcClient = jdbcClient;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void clearChatTables() {
        jdbcClient.sql("TRUNCATE TABLE chat_feedback, chat_messages, chat_sessions RESTART IDENTITY")
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
}
