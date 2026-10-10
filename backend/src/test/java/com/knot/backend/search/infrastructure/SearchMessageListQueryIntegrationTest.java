package com.knot.backend.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.search.SearchFixtures;
import com.knot.backend.search.SearchMessageFixtures;
import com.knot.backend.testsupport.TestcontainersConfiguration;
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
}
