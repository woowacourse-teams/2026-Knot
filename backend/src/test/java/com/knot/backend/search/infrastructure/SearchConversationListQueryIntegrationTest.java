package com.knot.backend.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.search.SearchFixtures;
import com.knot.backend.search.application.SearchConversationListQuery;
import com.knot.backend.search.application.dto.result.SearchConversationListItemResult;
import com.knot.backend.search.domain.SearchConversationCursor;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
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
@Import({TestcontainersConfiguration.class, SearchConversationListQueryAdapter.class})
class SearchConversationListQueryIntegrationTest {

    @Autowired
    private SearchConversationListQuery query;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private EntityManager entityManager;
    private DocumentFixtures members;
    private SearchFixtures fixtures;
    private long workspaceId;
    private long memberId;

    @BeforeEach
    void setUp() {
        members = new DocumentFixtures(jdbc);
        fixtures = new SearchFixtures(jdbc);
        workspaceId = members.saveWorkspace();
        memberId = members.saveMember("조회자");
    }

    @Test
    @DisplayName("개인 범위와 숨김 조건을 페이지 크기 적용 전에 거른다")
    void findPage_scopesAndVisibilityBeforeLimit() {
        long mine = fixtures.saveConversation(
                workspaceId,
                memberId,
                "내 질문",
                "답변"
        );
        fixtures.saveConversation(
                workspaceId,
                members.saveMember("다른 멤버"),
                "비공개",
                "비공개"
        );
        fixtures.saveConversation(
                members.saveWorkspace(),
                memberId,
                "다른 공간",
                "비공개"
        );
        long hidden = fixtures.saveConversation(
                workspaceId,
                memberId,
                "실패",
                "부분 답변"
        );
        fixtures.setVisible(
                hidden,
                false
        );
        fixtures.saveEmptyConversation(
                workspaceId,
                memberId,
                SearchFixtures.TIME
        );

        assertThat(
                query.findPage(
                        workspaceId,
                        memberId,
                        1,
                        null
                )
        ).extracting(SearchConversationListItemResult::id)
                .containsExactly(mine);
    }

    @Test
    @DisplayName("최근 활동과 ID 순서로 다음 페이지 확인용 한 행을 더 읽는다")
    void findPage_ordersByActivityThenIdAndReadsExtraRow() {
        long first = fixtures.saveConversation(
                workspaceId,
                memberId,
                "첫 질문",
                "첫 답변"
        );
        long second = fixtures.saveConversation(
                workspaceId,
                memberId,
                "둘째 질문",
                "둘째 답변"
        );
        long third = fixtures.saveConversation(
                workspaceId,
                memberId,
                "셋째 질문",
                "셋째 답변"
        );
        fixtures.setUpdatedAt(
                first,
                SearchFixtures.TIME.plusSeconds(1)
        );

        assertThat(
                query.findPage(
                        workspaceId,
                        memberId,
                        1,
                        null
                )
        ).extracting(SearchConversationListItemResult::id)
                .containsExactly(
                        first,
                        third
                );
        SearchConversationCursor cursor = SearchConversationCursor.of(
                workspaceId,
                memberId,
                SearchFixtures.TIME,
                third
        );
        assertThat(
                query.findPage(
                        workspaceId,
                        memberId,
                        100,
                        cursor
                )
        ).extracting(SearchConversationListItemResult::id)
                .containsExactly(second);
    }

    @Test
    @DisplayName("PostgreSQL의 마이크로초 시각으로 커서를 이어 읽는다")
    void findPage_keepsPostgresMicrosecondCursorPrecision() {
        long earlier = fixtures.saveConversation(
                workspaceId,
                memberId,
                "이전",
                "답변"
        );
        long later = fixtures.saveConversation(
                workspaceId,
                memberId,
                "이후",
                "답변"
        );
        fixtures.setUpdatedAt(
                later,
                SearchFixtures.TIME.plusNanos(1000)
        );
        List<SearchConversationListItemResult> page = query.findPage(
                workspaceId,
                memberId,
                1,
                null
        );
        SearchConversationListItemResult item = page.getFirst();
        SearchConversationCursor cursor = SearchConversationCursor.parse(
                SearchConversationCursor.of(
                        workspaceId,
                        memberId,
                        item.updatedAt(),
                        item.id()
                )
                        .encode(),
                workspaceId,
                memberId
        );

        assertThat(item.id()).isEqualTo(later);
        assertThat(
                query.findPage(
                        workspaceId,
                        memberId,
                        1,
                        cursor
                )
        ).extracting(SearchConversationListItemResult::id)
                .containsExactly(earlier);
    }

    @Test
    @DisplayName("첫 질문 제목과 최신 메시지를 파생하고 원문은 보존한다")
    void findPage_derivesFirstQuestionAndLatestMessageWithoutChangingSource() {
        long id = fixtures.saveConversation(
                workspaceId,
                memberId,
                "  첫\n질문  ",
                "이전 답변"
        );
        fixtures.saveMessage(
                id,
                "USER",
                3,
                "후속 질문",
                "RECEIVED"
        );
        fixtures.saveMessage(
                id,
                "ASSISTANT",
                4,
                "  최신\t부분 답변 ",
                "FAILED"
        );

        SearchConversationListItemResult result = query.findPage(
                workspaceId,
                memberId,
                20,
                null
        )
                .getFirst();

        assertThat(result.title()).isEqualTo("첫 질문");
        assertThat(result.lastMessagePreview()).isEqualTo("최신 부분 답변");
        assertThat(result.createdAt()).isEqualTo(SearchFixtures.TIME);
        assertThat(
                jdbc.sql("SELECT content FROM search_messages WHERE conversation_id = :id AND sequence = 1")
                        .param(
                                "id",
                                id
                        )
                        .query(String.class)
                        .single()
        ).isEqualTo("  첫\n질문  ");
    }

    @Test
    @DisplayName("생성 중 빈 답변은 빈 미리보기로 반환한다")
    void findPage_preservesEmptyStreamingPreview() {
        fixtures.saveConversation(
                workspaceId,
                memberId,
                "질문",
                ""
        );

        assertThat(
                query.findPage(
                        workspaceId,
                        memberId,
                        20,
                        null
                )
                        .getFirst()
                        .lastMessagePreview()
        ).isEmpty();
    }

    @Test
    @DisplayName("대화가 많아도 목록 조회 SQL은 한 번이다")
    void findPage_usesOneSelectForManyConversations() {
        for (int i = 0; i < 25; i++) {
            fixtures.saveConversation(
                    workspaceId,
                    memberId,
                    "질문 " + i,
                    "답변 " + i
            );
        }
        var statistics = entityManager.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class)
                .getStatistics();
        statistics.clear();

        assertThat(
                query.findPage(
                        workspaceId,
                        memberId,
                        20,
                        null
                )
        ).hasSize(21);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }
}
