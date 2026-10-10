package com.knot.backend.search.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.infrastructure.jwt.JwtProvider;
import com.knot.backend.global.config.JwtProperties;
import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.search.SearchFixtures;
import com.knot.backend.search.SearchMessageFixtures;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Tag("acceptance")
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
class SearchMessageListAcceptanceTest {
    private static final String PATH = "/api/v1/workspaces/{workspaceId}/search/conversations/{conversationId}/messages";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private AuthTokenProvider tokens;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private JsonMapper json;
    private DocumentFixtures documents;
    private SearchFixtures search;
    private SearchMessageFixtures messages;
    private long workspaceId;
    private long memberId;
    private long conversationId;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE TABLE members, workspaces RESTART IDENTITY CASCADE")
                .update();
        documents = new DocumentFixtures(jdbc);
        search = new SearchFixtures(jdbc);
        messages = new SearchMessageFixtures(jdbc);
        memberId = documents.saveMember("조회자");
        workspaceId = documents.saveWorkspace();
        documents.join(
                workspaceId,
                memberId
        );
        conversationId = search.saveConversation(
                workspaceId,
                memberId,
                " 질문\n😀 ",
                "  부분 답변\n😀 "
        );
    }

    @Test
    @DisplayName("쿠키로 전체 본문과 근거를 읽고 CSRF 없이 저장 상태를 유지한다")
    void find_success_contractAndReadOnly() throws Exception {
        // given
        long answer = messages.messageId(
                conversationId,
                2
        );
        long document = messages.saveDocument(
                workspaceId,
                memberId
        );
        messages.saveEvidence(
                answer,
                document,
                1
        );
        String before = snapshot();
        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(
                        jsonPath("$.items[0].id").value(
                                messages.messageId(
                                        conversationId,
                                        1
                                )
                        )
                )
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[0].sequence").value(1))
                .andExpect(jsonPath("$.items[0].content").value(" 질문\n😀 "))
                .andExpect(jsonPath("$.items[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$.items[0].evidences").isEmpty())
                .andExpect(jsonPath("$.items[1].id").value(answer))
                .andExpect(jsonPath("$.items[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.items[1].sequence").value(2))
                .andExpect(jsonPath("$.items[1].content").value("  부분 답변\n😀 "))
                .andExpect(jsonPath("$.items[1].status").value("STREAMING"))
                .andExpect(jsonPath("$.items[1].createdAt").value("2026-10-11T00:00:00.123456Z"))
                .andExpect(jsonPath("$.items[1].evidences[0].documentId").value(document))
                .andExpect(jsonPath("$.items[1].evidences[0].title").value("문서 보관 정책"))
                .andExpect(jsonPath("$.items[1].evidences[0].topic").value("탐색 테스트"))
                .andExpect(jsonPath("$.items[1].evidences[0].rank").value(1))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.previousCursor").value(nullValue()))
                .andExpect(jsonPath("$.excludedDocuments").doesNotExist());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("최신 페이지에서 과거 경계를 넘겨 마지막까지 중복 없이 읽는다")
    void find_success_pagination() throws Exception {
        // given
        addPairs(
                3,
                8
        );
        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                ).param(
                        "size",
                        "3"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].sequence").value(6))
                .andExpect(jsonPath("$.items[1].sequence").value(7))
                .andExpect(jsonPath("$.items[2].sequence").value(8))
                .andExpect(jsonPath("$.hasPrevious").value(true))
                .andExpect(jsonPath("$.previousCursor").value("6"));
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                ).param(
                        "size",
                        "3"
                )
                        .param(
                                "beforeSequence",
                                "6"
                        )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].sequence").value(3))
                .andExpect(jsonPath("$.items[1].sequence").value(4))
                .andExpect(jsonPath("$.items[2].sequence").value(5))
                .andExpect(jsonPath("$.previousCursor").value("3"));
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                ).param(
                        "size",
                        "3"
                )
                        .param(
                                "beforeSequence",
                                "3"
                        )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sequence").value(1))
                .andExpect(jsonPath("$.items[1].sequence").value(2))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.previousCursor").value(nullValue()));
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                ).param(
                        "beforeSequence",
                        "1"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.previousCursor").value(nullValue()));
    }

    @Test
    @DisplayName("페이지 크기는 기본 30이며 최대 100과 최소 1을 지원한다")
    void find_success_pageSizeBoundaries() throws Exception {
        // given
        addPairs(
                3,
                102
        );
        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(30))
                .andExpect(jsonPath("$.previousCursor").value("73"));
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                ).param(
                        "size",
                        "100"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(100))
                .andExpect(jsonPath("$.previousCursor").value("3"));
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                ).param(
                        "size",
                        "1"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sequence").value(102))
                .andExpect(jsonPath("$.previousCursor").value("102"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STREAMING", "COMPLETED", "FAILED", "STOPPED"})
    @DisplayName("답변의 상태와 긴 부분 본문을 그대로 복구한다")
    void find_success_answerStates(String answerStatus) throws Exception {
        // given
        String content = " \n😀\t" + "긴 답변".repeat(200);
        jdbc.sql(
                "UPDATE search_messages SET status = :status, content = :content WHERE conversation_id = :id AND sequence = 2"
        )
                .param(
                        "status",
                        answerStatus
                )
                .param(
                        "content",
                        content
                )
                .param(
                        "id",
                        conversationId
                )
                .update();
        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].content").value(content))
                .andExpect(jsonPath("$.items[1].status").value(answerStatus));
    }

    @Test
    @DisplayName("빈 STREAMING 본문과 숨긴 본인 대화도 현재 권한으로 복구한다")
    void find_success_hiddenConversationAndEmptyContent() throws Exception {
        // given
        search.setVisible(
                conversationId,
                false
        );
        jdbc.sql("UPDATE search_messages SET content = '' WHERE conversation_id = :id AND sequence = 2")
                .param(
                        "id",
                        conversationId
                )
                .update();
        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].content").value(""))
                .andExpect(jsonPath("$.items[1].evidences").isEmpty());
    }

    @Test
    @DisplayName("세 근거가 있어도 페이지는 메시지 개수로 선택하고 순위대로 반환한다")
    void find_success_threeEvidences() throws Exception {
        // given
        long answer = messages.messageId(
                conversationId,
                2
        );
        messages.saveEvidence(
                answer,
                messages.saveDocument(
                        workspaceId,
                        memberId
                ),
                3
        );
        messages.saveEvidence(
                answer,
                messages.saveDocument(
                        workspaceId,
                        memberId
                ),
                1
        );
        messages.saveEvidence(
                answer,
                messages.saveDocument(
                        workspaceId,
                        memberId
                ),
                2
        );
        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                ).param(
                        "size",
                        "1"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sequence").value(2))
                .andExpect(jsonPath("$.items[0].evidences.length()").value(3))
                .andExpect(jsonPath("$.items[0].evidences[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].evidences[1].rank").value(2))
                .andExpect(jsonPath("$.items[0].evidences[2].rank").value(3))
                .andExpect(jsonPath("$.previousCursor").value("2"));
    }

    @Test
    @DisplayName("미인증은 401이며 대화 존재 여부를 노출하지 않는다")
    void find_failure_unauthenticated() throws Exception {
        // when & then
        mvc.perform(
                get(
                        PATH,
                        workspaceId,
                        conversationId
                )
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(
                get(
                        PATH,
                        workspaceId,
                        conversationId
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
    @DisplayName("만료된 access token과 닉네임 용도 token은 401이다")
    void find_failure_expiredOrWrongPurposeToken() throws Exception {
        // given
        Instant issuedAt = Instant.now()
                .minus(jwtProperties.getExpiration())
                .minusSeconds(60);
        JwtProvider issuer = new JwtProvider(
                jwtProperties,
                Clock.fixed(
                        issuedAt,
                        ZoneOffset.UTC
                )
        );
        String expired = issuer.issue(
                AuthenticatedMember.of(
                        memberId,
                        "조회자",
                        null
                )
        );
        String onboarding = tokens.issueNickname(
                OAuthUser.of(
                        OAuthProvider.GITHUB,
                        "search-onboarding",
                        null
                )
        );
        // when & then
        for (String token : List.of(
                expired,
                onboarding
        )) {
            mvc.perform(
                    get(
                            PATH,
                            workspaceId,
                            conversationId
                    ).cookie(
                            new Cookie(
                                    "KNOT_ACCESS_TOKEN",
                                    token
                            )
                    )
            )
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    @DisplayName("같은 Workspace의 다른 멤버도 개인 대화를 읽을 수 없다")
    void find_failure_otherOwner() throws Exception {
        // given
        long other = documents.saveMember("다른 멤버");
        documents.join(
                workspaceId,
                other
        );
        // when & then
        expectAccessDenied(
                workspaceId,
                conversationId,
                other
        );
    }

    @Test
    @DisplayName("현재 멤버여도 다른 Workspace 대화는 읽을 수 없다")
    void find_failure_otherWorkspace() throws Exception {
        // given
        long another = documents.saveWorkspace();
        documents.join(
                another,
                memberId
        );
        // when & then
        expectAccessDenied(
                another,
                conversationId,
                memberId
        );
    }

    @Test
    @DisplayName("미가입 Workspace와 탈퇴 후 접근은 403이다")
    void find_failure_inactiveMembership() throws Exception {
        // when & then
        expectAccessDenied(
                documents.saveWorkspace(),
                conversationId,
                memberId
        );
        // given
        documents.leave(
                workspaceId,
                memberId
        );
        // when & then
        expectAccessDenied(
                workspaceId,
                conversationId,
                memberId
        );
    }

    @Test
    @DisplayName("삭제된 Workspace는 읽을 수 없다")
    void find_failure_deletedWorkspace() throws Exception {
        // given
        jdbc.sql("UPDATE workspaces SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param(
                        "id",
                        workspaceId
                )
                .update();
        // when & then
        expectAccessDenied(
                workspaceId,
                conversationId,
                memberId
        );
    }

    @Test
    @DisplayName("현재 멤버가 요청한 존재하지 않는 대화는 404다")
    void find_failure_missingConversation() throws Exception {
        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        Long.MAX_VALUE,
                        memberId
                )
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    @ParameterizedTest
    @CsvSource({"size,0", "size,-1", "size,101", "size,text", "size,2147483648", "beforeSequence,0",
            "beforeSequence,-1", "beforeSequence,text", "beforeSequence,2147483648"})
    @DisplayName("잘못된 페이지 입력은 400 INVALID_PARAMETER다")
    void find_failure_invalidParameter(
            String key,
            String value
    ) throws Exception {
        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        conversationId,
                        memberId
                ).param(
                        key,
                        value
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("양수가 아닌 경로는 400 INVALID_PARAMETER다")
    void find_failure_invalidPath(long id) throws Exception {
        // when & then
        mvc.perform(
                request(
                        id,
                        conversationId,
                        memberId
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(
                request(
                        workspaceId,
                        id,
                        memberId
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("OpenAPI에 메시지와 근거 및 모든 오류 schema를 문서화한다")
    void find_success_openApi() throws Exception {
        // when
        String response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode api = json.readTree(response);
        JsonNode paths = api.get("paths");
        JsonNode path = paths.get(PATH);
        // then
        assertThat(path).isNotNull();
        JsonNode operation = path.get("get");
        JsonNode responses = operation.get("responses");
        for (String code : List.of(
                "200",
                "400",
                "401",
                "403",
                "404"
        )) {
            assertThat(responses.has(code)).isTrue();
        }
        assertThat(operation.toString()).doesNotContain("X-XSRF-TOKEN");
        JsonNode components = api.get("components");
        JsonNode schemas = components.get("schemas");
        JsonNode schema = schemas.get("SearchMessageListResponse");
        assertThat(
                schema.get("required")
                        .toString()
        ).contains(
                "conversationId",
                "items",
                "hasPrevious",
                "previousCursor"
        );
    }

    private void addPairs(
            int start,
            int end
    ) {
        for (int sequence = start; sequence < end; sequence += 2) {
            search.saveMessage(
                    conversationId,
                    "USER",
                    sequence,
                    "질문",
                    "RECEIVED"
            );
            search.saveMessage(
                    conversationId,
                    "ASSISTANT",
                    sequence + 1,
                    "답변",
                    "COMPLETED"
            );
        }
    }

    private MockHttpServletRequestBuilder request(
            long workspace,
            long conversation,
            long member
    ) {
        String token = tokens.issue(
                AuthenticatedMember.of(
                        member,
                        "멤버",
                        null
                )
        );
        return get(
                PATH,
                workspace,
                conversation
        ).cookie(
                new Cookie(
                        "KNOT_ACCESS_TOKEN",
                        token
                )
        );
    }

    private void expectAccessDenied(
            long workspace,
            long conversation,
            long member
    ) throws Exception {
        mvc.perform(
                request(
                        workspace,
                        conversation,
                        member
                )
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SEARCH_ACCESS_DENIED"))
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    private String snapshot() {
        StringBuilder result = new StringBuilder(documents.snapshot());
        for (String table : List.of(
                "search_conversations",
                "search_messages",
                "search_evidences"
        )) {
            result.append(
                    jdbc.sql("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY 1")
                            .query(String.class)
                            .list()
            );
        }
        return result.toString();
    }
}
