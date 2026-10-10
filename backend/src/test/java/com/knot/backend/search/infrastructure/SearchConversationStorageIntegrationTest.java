package com.knot.backend.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.search.SearchFixtures;
import com.knot.backend.search.domain.SearchConversation;
import com.knot.backend.search.domain.SearchMessage;
import com.knot.backend.search.domain.SearchMessageRole;
import com.knot.backend.search.domain.SearchMessageStatus;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class SearchConversationStorageIntegrationTest {

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private EntityManager entities;
    private SearchFixtures search;
    private long workspaceId;
    private long memberId;

    @BeforeEach
    void setUp() {
        DocumentFixtures base = new DocumentFixtures(jdbc);
        workspaceId = base.saveWorkspace();
        memberId = base.saveMember("탐색자");
        search = new SearchFixtures(jdbc);
    }

    @Test
    @DisplayName("Flyway 스키마와 Entity 매핑 및 빈 STREAMING 본문을 실제 PostgreSQL에서 읽는다")
    void mapping_success() {
        // given
        long id = search.saveConversation(
                workspaceId,
                memberId,
                "질문",
                ""
        );

        // when
        SearchConversation conversation = entities.find(
                SearchConversation.class,
                id
        );
        SearchMessage answer = entities
                .createQuery(
                        "select m from SearchMessage m where m.conversationId = :id and m.sequence = 2",
                        SearchMessage.class
                )
                .setParameter(
                        "id",
                        id
                )
                .getSingleResult();

        // then
        assertThat(conversation.getUpdatedAt()).isEqualTo(SearchFixtures.TIME);
        assertThat(answer.getRole()).isEqualTo(SearchMessageRole.ASSISTANT);
        assertThat(answer.getStatus()).isEqualTo(SearchMessageStatus.STREAMING);
        assertThat(answer.getContent()).isEmpty();
    }

    @Test
    @DisplayName("최초 답변 실패와 재시도 중 숨김은 DB에 유지되고 완료 시 복원된다")
    void visibilityPersistence_success() {
        // given
        long id = search.saveConversation(
                workspaceId,
                memberId,
                "질문",
                "일부 답변"
        );
        SearchConversation conversation = entities.find(
                SearchConversation.class,
                id
        );

        // when & then
        conversation.recordFirstAnswerStatus(SearchMessageStatus.FAILED);
        entities.flush();
        assertThat(readVisible(id)).isFalse();
        conversation.recordFirstAnswerStatus(SearchMessageStatus.STREAMING);
        entities.flush();
        assertThat(readVisible(id)).isFalse();
        conversation.recordFirstAnswerStatus(SearchMessageStatus.COMPLETED);
        entities.flush();
        assertThat(readVisible(id)).isTrue();
    }

    @Test
    @DisplayName("같은 대화의 중복 sequence는 DB에서 거절한다")
    void duplicateSequence_failure() {
        // given
        long id = search.saveConversation(
                workspaceId,
                memberId,
                "질문",
                "답변"
        );

        // when & then
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> search.saveMessage(
                        id,
                        "USER",
                        1,
                        "중복 질문",
                        "RECEIVED"
                )
        );
    }

    @ParameterizedTest
    @CsvSource({"USER,STREAMING", "ASSISTANT,RECEIVED", "SYSTEM,COMPLETED", "ASSISTANT,UNKNOWN"})
    @DisplayName("role과 status 허용 조합은 DB CHECK로 제한한다")
    void invalidRoleStatus_failure(
            String role,
            String status
    ) {
        // given
        long id = search.saveEmptyConversation(
                workspaceId,
                memberId,
                SearchFixtures.TIME
        );

        // when & then
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> search.saveMessage(
                        id,
                        role,
                        1,
                        "본문",
                        status
                )
        );
    }

    @Test
    @DisplayName("양수가 아닌 sequence는 DB에서 거절한다")
    void invalidSequence_failure() {
        // given
        long id = search.saveEmptyConversation(
                workspaceId,
                memberId,
                SearchFixtures.TIME
        );

        // when & then
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> search.saveMessage(
                        id,
                        "USER",
                        0,
                        "질문",
                        "RECEIVED"
                )
        );
    }

    @Test
    @DisplayName("없는 대화에 메시지를 저장하면 FK가 거절한다")
    void unknownConversation_failure() {
        // when & then
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> search.saveMessage(
                        Long.MAX_VALUE,
                        "USER",
                        1,
                        "질문",
                        "RECEIVED"
                )
        );
    }

    @Test
    @DisplayName("메시지가 참조하는 대화를 삭제하면 RESTRICT가 거절한다")
    void deleteReferencedConversation_failure() {
        // given
        long id = search.saveConversation(
                workspaceId,
                memberId,
                "질문",
                "답변"
        );

        // when & then
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
                () -> jdbc.sql("DELETE FROM search_conversations WHERE id = :id")
                        .param(
                                "id",
                                id
                        )
                        .update()
        );
    }

    private boolean readVisible(long id) {
        return jdbc.sql("SELECT visible_in_list FROM search_conversations WHERE id = :id")
                .param(
                        "id",
                        id
                )
                .query(Boolean.class)
                .single();
    }
}
