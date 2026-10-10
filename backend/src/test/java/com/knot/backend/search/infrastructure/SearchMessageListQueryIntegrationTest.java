package com.knot.backend.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.search.SearchFixtures;
import com.knot.backend.search.SearchMessageFixtures;
import com.knot.backend.search.application.dto.result.SearchEvidenceItemResult;
import com.knot.backend.search.domain.SearchEvidence;
import com.knot.backend.search.domain.SearchEvidenceId;
import com.knot.backend.search.domain.SearchMessage;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({TestcontainersConfiguration.class, SearchMessageListQueryAdapter.class})
class SearchMessageListQueryIntegrationTest {

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private EntityManager entities;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private SearchMessageListQueryAdapter query;
    private DocumentFixtures documents;
    private SearchFixtures search;
    private SearchMessageFixtures messages;
    private long workspaceId;
    private long memberId;
    private long conversationId;

    @BeforeEach
    void setUp() {
        documents = new DocumentFixtures(jdbc);
        search = new SearchFixtures(jdbc);
        messages = new SearchMessageFixtures(jdbc);
        workspaceId = documents.saveWorkspace();
        memberId = documents.saveMember("조회자");
        documents.join(
                workspaceId,
                memberId
        );
        conversationId = search.saveConversation(
                workspaceId,
                memberId,
                "질문",
                "답변"
        );
    }

    @Test
    @DisplayName("숨긴 대화도 단건 조회하며 없는 대화는 비어 있다")
    void findConversation_success() {
        // given
        search.setVisible(
                conversationId,
                false
        );
        // when & then
        assertThat(query.findConversation(conversationId)).isPresent();
        assertThat(query.findConversation(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("생성 시각이 같아도 순서로 최신 페이지와 과거 경계를 선택한다")
    void findPage_success() {
        // given
        search.saveMessage(
                conversationId,
                "USER",
                3,
                "새 질문",
                "RECEIVED"
        );
        search.saveMessage(
                conversationId,
                "ASSISTANT",
                4,
                "",
                "STREAMING"
        );
        // when
        List<SearchMessage> latest = query.findPage(
                workspaceId,
                memberId,
                conversationId,
                null,
                2
        );
        List<SearchMessage> previous = query.findPage(
                workspaceId,
                memberId,
                conversationId,
                3,
                2
        );
        // then
        assertThat(latest).extracting(SearchMessage::getSequence)
                .containsExactly(
                        4,
                        3,
                        2
                );
        assertThat(previous).extracting(SearchMessage::getSequence)
                .containsExactly(
                        2,
                        1
                );
        SearchMessage latestMessage = latest.getFirst();
        assertThat(latestMessage.getContent()).isEmpty();
        assertThat(
                query.findPage(
                        workspaceId,
                        memberId,
                        conversationId,
                        1,
                        30
                )
        ).isEmpty();
    }

    @Test
    @DisplayName("메시지 조회 SQL에도 소유자와 Workspace 범위를 적용한다")
    void findPage_failure_wrongScope() {
        // when & then
        assertThat(
                query.findPage(
                        workspaceId,
                        Long.MAX_VALUE,
                        conversationId,
                        null,
                        30
                )
        ).isEmpty();
        assertThat(
                query.findPage(
                        Long.MAX_VALUE,
                        memberId,
                        conversationId,
                        null,
                        30
                )
        ).isEmpty();
        assertThat(
                query.findPage(
                        workspaceId,
                        memberId,
                        Long.MAX_VALUE,
                        null,
                        30
                )
        ).isEmpty();
    }

    @Test
    @DisplayName("한 페이지의 답변별 세 근거를 한 SQL로 순위대로 복구한다")
    void findEvidences_success_bulkAndMapping() {
        // given
        long answer = messages.messageId(
                conversationId,
                2
        );
        long third = messages.saveDocument(
                workspaceId,
                memberId
        );
        long first = messages.saveDocument(
                workspaceId,
                memberId
        );
        long second = messages.saveDocument(
                workspaceId,
                memberId
        );
        messages.saveEvidence(
                answer,
                third,
                3
        );
        messages.saveEvidence(
                answer,
                first,
                1
        );
        messages.saveEvidence(
                answer,
                second,
                2
        );
        Statistics statistics = statistics();
        statistics.clear();
        // when
        Map<Long, List<SearchEvidenceItemResult>> result = query.findEvidences(
                workspaceId,
                memberId,
                conversationId,
                List.of(answer)
        );
        // then
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(result.get(answer)).extracting(SearchEvidenceItemResult::documentId)
                .containsExactly(
                        first,
                        second,
                        third
                );
        assertThat(result.get(answer)).extracting(SearchEvidenceItemResult::rank)
                .containsExactly(
                        1,
                        2,
                        3
                );
        assertThat(result.get(answer)).extracting(SearchEvidenceItemResult::title)
                .containsOnly("문서 보관 정책");
        assertThat(result.get(answer)).extracting(SearchEvidenceItemResult::topic)
                .containsOnly("탐색 테스트");
        SearchEvidence stored = entities
                .createQuery(
                        "select e from SearchEvidence e where e.id.messageId = :message and e.rank = 1",
                        SearchEvidence.class
                )
                .setParameter(
                        "message",
                        answer
                )
                .getSingleResult();
        assertThat(stored.getRank()).isEqualTo((short) 1);
        SearchEvidenceId storedId = stored.getId();
        assertThat(storedId.getMessageId()).isEqualTo(answer);
        assertThat(storedId.getDocumentId()).isEqualTo(first);
    }

    @Test
    @DisplayName("여러 답변의 근거와 페이지 밖 근거를 구분하고 SQL 수는 늘지 않는다")
    void findEvidences_success_multipleAnswers() {
        // given
        search.saveMessage(
                conversationId,
                "USER",
                3,
                "후속",
                "RECEIVED"
        );
        search.saveMessage(
                conversationId,
                "ASSISTANT",
                4,
                "다음",
                "COMPLETED"
        );
        long firstAnswer = messages.messageId(
                conversationId,
                2
        );
        long nextAnswer = messages.messageId(
                conversationId,
                4
        );
        long firstDocument = messages.saveDocument(
                workspaceId,
                memberId
        );
        long nextDocument = messages.saveDocument(
                workspaceId,
                memberId
        );
        messages.saveEvidence(
                firstAnswer,
                firstDocument,
                1
        );
        messages.saveEvidence(
                nextAnswer,
                nextDocument,
                1
        );
        Statistics statistics = statistics();
        statistics.clear();
        // when
        Map<Long, List<SearchEvidenceItemResult>> result = query.findEvidences(
                workspaceId,
                memberId,
                conversationId,
                List.of(
                        firstAnswer,
                        nextAnswer
                )
        );
        // then
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(result).containsOnlyKeys(
                firstAnswer,
                nextAnswer
        );
        assertThat(
                query.findEvidences(
                        workspaceId,
                        memberId,
                        conversationId,
                        List.of(firstAnswer)
                )
        ).containsOnlyKeys(firstAnswer);
    }

    @Test
    @DisplayName("잘못 저장된 USER 근거와 다른 Workspace 문서를 노출하지 않는다")
    void findEvidences_failure_invalidConnections() {
        // given
        long user = messages.messageId(
                conversationId,
                1
        );
        long answer = messages.messageId(
                conversationId,
                2
        );
        long local = messages.saveDocument(
                workspaceId,
                memberId
        );
        long foreign = messages.saveDocument(
                documents.saveWorkspace(),
                memberId
        );
        messages.saveEvidence(
                user,
                local,
                1
        );
        messages.saveEvidence(
                answer,
                foreign,
                1
        );
        // when & then
        assertThat(
                query.findEvidences(
                        workspaceId,
                        memberId,
                        conversationId,
                        List.of(
                                user,
                                answer
                        )
                )
        ).isEmpty();
        assertThat(
                query.findEvidences(
                        workspaceId,
                        Long.MAX_VALUE,
                        conversationId,
                        List.of(
                                user,
                                answer
                        )
                )
        ).isEmpty();
    }

    @Test
    @DisplayName("다른 대화의 답변 ID를 전달해도 근거를 노출하지 않는다")
    void findEvidences_failure_otherConversation() {
        // given
        long other = search.saveConversation(
                workspaceId,
                memberId,
                "다른",
                "답변"
        );
        long answer = messages.messageId(
                other,
                2
        );
        messages.saveEvidence(
                answer,
                messages.saveDocument(
                        workspaceId,
                        memberId
                ),
                1
        );
        // when & then
        assertThat(
                query.findEvidences(
                        workspaceId,
                        memberId,
                        conversationId,
                        List.of(answer)
                )
        ).isEmpty();
    }

    @Test
    @DisplayName("빈 답변 ID 목록은 SQL 없이 빈 근거를 반환한다")
    void findEvidencesEmpty_success() {
        // given
        Statistics statistics = statistics();
        statistics.clear();
        // when
        Map<Long, List<SearchEvidenceItemResult>> result = query.findEvidences(
                workspaceId,
                memberId,
                conversationId,
                List.of()
        );
        // then
        assertThat(result).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    private Statistics statistics() {
        SessionFactory sessions = entityManagerFactory.unwrap(SessionFactory.class);
        return sessions.getStatistics();
    }
}
