package com.knot.backend.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.search.SearchFixtures;
import com.knot.backend.search.SearchMessageFixtures;
import com.knot.backend.search.domain.SearchMessage;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.util.List;
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
}
