package com.knot.backend.search.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.search.SearchFixtures;
import com.knot.backend.search.domain.SearchConversationCursor;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@Tag("acceptance")
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
class SearchConversationListAcceptanceTest {
    private static final String PATH = "/api/v1/workspaces/{workspaceId}/search/conversations";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private AuthTokenProvider tokens;
    @Autowired
    private JsonMapper json;
    private DocumentFixtures members;
    private SearchFixtures fixtures;
    private long workspaceId;
    private long memberId;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE TABLE members, workspaces RESTART IDENTITY CASCADE")
                .update();
        members = new DocumentFixtures(jdbc);
        fixtures = new SearchFixtures(jdbc);
        memberId = members.saveMember("조회자");
        workspaceId = members.saveWorkspace();
        members.join(
                workspaceId,
                memberId
        );
    }

    @Test
    @DisplayName("대화가 없으면 빈 목록과 null 커서를 반환한다")
    void find_emptyWorkspace() throws Exception {
        mvc.perform(request(memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("쿠키 인증으로 응답 계약을 읽고 저장 상태는 바꾸지 않는다")
    void find_cookieAuthenticationResponseAndReadOnly() throws Exception {
        long id = fixtures.saveConversation(
                workspaceId,
                memberId,
                "  질문\n제목  ",
                " 답변\t미리보기 "
        );
        String before = snapshot();

        mvc.perform(request(memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].title").value("질문 제목"))
                .andExpect(jsonPath("$.items[0].lastMessagePreview").value("답변 미리보기"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-10-11T00:00:00.123456Z"))
                .andExpect(jsonPath("$.items[0].updatedAt").value("2026-10-11T00:00:00.123456Z"))
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("다른 소유자와 숨긴 첫 실패 대화는 제외하고 성공 후 복원한다")
    void find_privateConversationsAndHiddenFirst_failure() throws Exception {
        long mine = fixtures.saveConversation(
                workspaceId,
                memberId,
                "질문",
                ""
        );
        long otherMember = members.saveMember("다른 멤버");
        members.join(
                workspaceId,
                otherMember
        );
        fixtures.saveConversation(
                workspaceId,
                otherMember,
                "비공개 질문",
                "비공개 답변"
        );
        fixtures.saveConversation(
                members.saveWorkspace(),
                memberId,
                "다른 공간",
                "다른 공간"
        );
        long failed = fixtures.saveConversation(
                workspaceId,
                memberId,
                "실패한 첫 질문",
                "부분 답변"
        );
        fixtures.setAnswerStatus(
                failed,
                "FAILED"
        );
        fixtures.setVisible(
                failed,
                false
        );
        fixtures.saveEmptyConversation(
                workspaceId,
                memberId,
                SearchFixtures.TIME
        );

        mvc.perform(
                request(memberId).param(
                        "size",
                        "1"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(mine))
                .andExpect(jsonPath("$.items[0].lastMessagePreview").value(""))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        fixtures.setAnswerStatus(
                failed,
                "STREAMING"
        );
        mvc.perform(request(memberId))
                .andExpect(jsonPath("$.items.length()").value(1));
        fixtures.setAnswerStatus(
                failed,
                "COMPLETED"
        );
        fixtures.setVisible(
                failed,
                true
        );
        mvc.perform(request(memberId))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(failed));
    }

    @Test
    @DisplayName("같은 시각의 대화를 크기를 바꿔 중복 없이 이어 읽는다")
    void find_cursorPagesCanChangeSizeWithoutDuplicates() throws Exception {
        long first = fixtures.saveConversation(
                workspaceId,
                memberId,
                "첫째",
                "답변"
        );
        long second = fixtures.saveConversation(
                workspaceId,
                memberId,
                "둘째",
                "답변"
        );
        long third = fixtures.saveConversation(
                workspaceId,
                memberId,
                "셋째",
                "답변"
        );
        String body = mvc.perform(
                request(memberId).param(
                        "size",
                        "1"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(third))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = json.readTree(body)
                .get("nextCursor")
                .asString();

        mvc.perform(
                request(memberId).param(
                        "size",
                        "100"
                )
                        .param(
                                "cursor",
                                cursor
                        )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.items[1].id").value(first))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("기본 20개와 최대 100개를 반환한다")
    void find_defaultTwentyAndMaximumHundred() throws Exception {
        for (int i = 0; i < 101; i++) {
            fixtures.saveConversation(
                    workspaceId,
                    memberId,
                    "질문 " + i,
                    "답변"
            );
        }
        mvc.perform(request(memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.nextCursor").isString());
        mvc.perform(
                request(memberId).param(
                        "size",
                        "100"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(100))
                .andExpect(jsonPath("$.nextCursor").isString());
    }

    @Test
    @DisplayName("누락하거나 잘못된 쿠키는 401로 거절한다")
    void find_requiresAuthentication() throws Exception {
        mvc.perform(
                get(
                        PATH,
                        workspaceId
                )
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(
                get(
                        PATH,
                        workspaceId
                ).cookie(
                        new Cookie(
                                "KNOT_ACCESS_TOKEN",
                                "invalid"
                        )
                )
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("현재 멤버가 아니거나 탈퇴한 멤버는 403으로 거절한다")
    void find_rejectsNonMemberAndFormerMember() throws Exception {
        long stranger = members.saveMember("비회원");
        mvc.perform(request(stranger))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
        members.leave(
                workspaceId,
                memberId
        );
        mvc.perform(request(memberId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("삭제된 Workspace는 403으로 거절한다")
    void find_rejectsDeletedWorkspace() throws Exception {
        jdbc.sql("UPDATE workspaces SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param(
                        "id",
                        workspaceId
                )
                .update();
        mvc.perform(request(memberId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "abc", "2147483648"})
    @DisplayName("크기 형식과 허용 범위를 벗어나면 400으로 거절한다")
    void find_rejectsInvalidSize(String size) throws Exception {
        mvc.perform(
                request(memberId).param(
                        "size",
                        size
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "%%%", "broken"})
    @DisplayName("빈 커서와 손상된 커서는 400으로 거절한다")
    void find_rejectsInvalidCursor(String cursor) throws Exception {
        mvc.perform(
                request(memberId).param(
                        "cursor",
                        cursor
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("다른 Workspace 또는 멤버의 커서는 400으로 거절한다")
    void find_rejectsCursorFromOtherScope() throws Exception {
        String otherMember = SearchConversationCursor.of(
                workspaceId,
                memberId + 1,
                SearchFixtures.TIME,
                1
        )
                .encode();
        String otherWorkspace = SearchConversationCursor.of(
                workspaceId + 1,
                memberId,
                SearchFixtures.TIME,
                1
        )
                .encode();
        mvc.perform(
                request(memberId).param(
                        "cursor",
                        otherMember
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(
                request(memberId).param(
                        "cursor",
                        otherWorkspace
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("OpenAPI에 GET 응답과 인증 오류 계약이 포함된다")
    void find_openApiDocumentsContract() throws Exception {
        String response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var api = json.readTree(response);
        var operation = api.get("paths")
                .get(PATH)
                .get("get");

        assertThat(
                operation.get("responses")
                        .has("200")
        ).isTrue();
        assertThat(
                operation.get("responses")
                        .has("400")
        ).isTrue();
        assertThat(
                operation.get("responses")
                        .has("401")
        ).isTrue();
        assertThat(
                operation.get("responses")
                        .has("403")
        ).isTrue();
        assertThat(operation.toString()).doesNotContain("X-XSRF-TOKEN");
        assertThat(
                api.get("components")
                        .get("schemas")
                        .get("SearchConversationListResponse")
                        .get("required")
                        .toString()
        ).contains(
                "items",
                "nextCursor"
        );
    }

    private String snapshot() {
        return jdbc.sql("SELECT row_to_json(c)::text FROM search_conversations c ORDER BY id")
                .query(String.class)
                .list()
                .toString()
                + jdbc.sql("SELECT row_to_json(m)::text FROM search_messages m ORDER BY id")
                        .query(String.class)
                        .list()
                        .toString();
    }

    private MockHttpServletRequestBuilder request(long member) {
        String token = tokens.issue(
                AuthenticatedMember.of(
                        member,
                        "멤버",
                        null
                )
        );
        return get(
                PATH,
                workspaceId
        ).cookie(
                new Cookie(
                        "KNOT_ACCESS_TOKEN",
                        token
                )
        );
    }
}
