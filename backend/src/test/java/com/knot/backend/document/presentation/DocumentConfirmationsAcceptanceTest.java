package com.knot.backend.document.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.domain.DocumentConfirmationCursor;
import com.knot.backend.document.domain.DocumentConfirmationState;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Tag("acceptance")
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
class DocumentConfirmationsAcceptanceTest {
    private static final String PATH = "/api/v1/workspaces/{workspaceId}/documents/{documentId}/confirmations";
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private AuthTokenProvider tokens;
    private DocumentFixtures fixtures;
    private long workspaceId;
    private long readerId;
    private long targetId;
    private long documentId;
    private long transcriptId;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE TABLE members, workspaces RESTART IDENTITY CASCADE")
                .update();
        fixtures = new DocumentFixtures(jdbc);
        targetId = fixtures.saveMember("기존 대상");
        readerId = fixtures.saveMember("새 가입자");
        workspaceId = fixtures.saveWorkspace();
        fixtures.join(
                workspaceId,
                targetId
        );
        long recordingId = fixtures.saveRecording(
                workspaceId,
                targetId,
                1000
        );
        transcriptId = fixtures.saveTranscript(recordingId);
        documentId = fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                fixtures.saveJob(
                        transcriptId,
                        "SUCCEEDED"
                ),
                "정책"
        );
        fixtures.target(
                documentId,
                targetId,
                null
        );
        fixtures.join(
                workspaceId,
                readerId
        );
    }

    @Test
    @DisplayName("이후 가입자는 대상에 추가되지 않고 부분 성공 문서의 현황을 읽기만 한다")
    void findConfirmations_success_readOnlyByNewMember() throws Exception {
        // given
        fixtures.saveJob(
                transcriptId,
                "FAILED"
        );
        String before = fixtures.snapshot();

        // when & then
        request().andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.confirmedCount").value(0))
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.excludedCount").value(0))
                .andExpect(jsonPath("$.confirmedByMe").value(false))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].memberId").value(targetId))
                .andExpect(jsonPath("$.items[0].nickname").value("기존 대상"))
                .andExpect(jsonPath("$.items[0].profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.items[0].confirmedAt").value(nullValue()))
                .andExpect(jsonPath("$.items[0].state").value("PENDING"))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        assertThat(fixtures.snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("확인한 탈퇴자도 CONFIRMED이며 전체 집계는 모든 페이지에서 동일하다")
    void findConfirmations_success_statePagesAndFullSummary() throws Exception {
        // given
        fixtures.target(
                documentId,
                readerId,
                DocumentFixtures.CREATED_AT
        );
        long departedId = fixtures.saveMember("탈퇴 확인자");
        fixtures.join(
                workspaceId,
                departedId
        );
        fixtures.target(
                documentId,
                departedId,
                DocumentFixtures.CREATED_AT
        );
        fixtures.leave(
                workspaceId,
                departedId
        );
        long excludedId = fixtures.saveMember("탈퇴 미확인자");
        fixtures.join(
                workspaceId,
                excludedId
        );
        fixtures.target(
                documentId,
                excludedId,
                null
        );
        fixtures.leave(
                workspaceId,
                excludedId
        );
        jdbc.sql("UPDATE members SET profile_image_url = 'https://example.com/profile.png' WHERE id = :id")
                .param(
                        "id",
                        readerId
                )
                .update();

        // when & then
        mvc.perform(
                builder(
                        workspaceId,
                        documentId,
                        readerId
                ).param(
                        "size",
                        "1"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(2))
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.excludedCount").value(1))
                .andExpect(jsonPath("$.confirmedByMe").value(true))
                .andExpect(jsonPath("$.items[0].memberId").value(readerId))
                .andExpect(jsonPath("$.items[0].confirmedAt").value("2026-10-06T00:00:00Z"))
                .andExpect(jsonPath("$.items[0].profileImageUrl").value("https://example.com/profile.png"))
                .andExpect(
                        jsonPath("$.nextCursor").value(
                                cursor(
                                        DocumentConfirmationState.CONFIRMED,
                                        readerId
                                )
                        )
                );
        mvc.perform(
                builder(
                        workspaceId,
                        documentId,
                        readerId
                ).param(
                        "size",
                        "1"
                )
                        .param(
                                "cursor",
                                cursor(
                                        DocumentConfirmationState.CONFIRMED,
                                        readerId
                                )
                        )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].memberId").value(departedId))
                .andExpect(jsonPath("$.items[0].state").value("CONFIRMED"));
        mvc.perform(
                builder(
                        workspaceId,
                        documentId,
                        readerId
                ).param(
                        "size",
                        "2"
                )
                        .param(
                                "cursor",
                                cursor(
                                        DocumentConfirmationState.CONFIRMED,
                                        departedId
                                )
                        )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(2))
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.excludedCount").value(1))
                .andExpect(jsonPath("$.items[0].state").value("PENDING"))
                .andExpect(jsonPath("$.items[1].state").value("EXCLUDED"))
                .andExpect(jsonPath("$.items[1].memberId").value(excludedId))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("대상이 없는 문서는 200과 영 집계 및 빈 배열을 반환한다")
    void findConfirmations_success_emptyTargets() throws Exception {
        // given
        jdbc.sql("DELETE FROM document_confirmations WHERE document_id = :id")
                .param(
                        "id",
                        documentId
                )
                .update();

        // when & then
        request().andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(0))
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andExpect(jsonPath("$.excludedCount").value(0))
                .andExpect(jsonPath("$.confirmedByMe").value(false))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("기본 페이지는 50명이며 최대 100명과 정확한 마지막 페이지를 지원한다")
    void findConfirmations_success_defaultAndMaximumSize() throws Exception {
        // given
        for (int index = 0; index < 50; index++) {
            long memberId = fixtures.saveMember("대상" + index);
            fixtures.join(
                    workspaceId,
                    memberId
            );
            fixtures.target(
                    documentId,
                    memberId,
                    null
            );
        }

        // when & then
        request().andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(51))
                .andExpect(jsonPath("$.items.length()").value(50))
                .andExpect(jsonPath("$.nextCursor").isString());
        mvc.perform(
                builder(
                        workspaceId,
                        documentId,
                        readerId
                ).param(
                        "size",
                        "100"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(51))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "2147483648", "invalid"})
    @DisplayName("범위 밖이거나 정수가 아닌 size는 400이다")
    void findConfirmations_failure_invalidSize(String size) throws Exception {
        // when & then
        mvc.perform(
                builder(
                        workspaceId,
                        documentId,
                        readerId
                ).param(
                        "size",
                        size
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid", "!!!!"})
    @DisplayName("빈 값이나 해석할 수 없는 cursor는 400이다")
    void findConfirmations_failure_invalidCursor(String cursor) throws Exception {
        // when & then
        mvc.perform(
                builder(
                        workspaceId,
                        documentId,
                        readerId
                ).param(
                        "cursor",
                        cursor
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("다른 멤버에게 발급된 cursor는 400이다")
    void findConfirmations_failure_cursorContextMismatch() throws Exception {
        // given
        String cursor = DocumentConfirmationCursor.of(
                workspaceId,
                documentId,
                targetId,
                DocumentConfirmationState.PENDING,
                targetId
        )
                .encode();

        // when & then
        mvc.perform(
                builder(
                        workspaceId,
                        documentId,
                        readerId
                ).param(
                        "cursor",
                        cursor
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("미인증 요청은 401이다")
    void findConfirmations_failure_unauthenticated() throws Exception {
        // when & then
        mvc.perform(
                get(
                        PATH,
                        workspaceId,
                        documentId
                )
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("비멤버와 탈퇴 멤버는 403이다")
    void findConfirmations_failure_workspaceAccessDenied() throws Exception {
        // given
        long outsider = fixtures.saveMember("외부인");
        fixtures.leave(
                workspaceId,
                readerId
        );

        // when & then
        mvc.perform(
                builder(
                        workspaceId,
                        documentId,
                        outsider
                )
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
        request().andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("삭제된 Workspace는 현재 멤버도 403이다")
    void findConfirmations_failure_deletedWorkspace() throws Exception {
        // given
        jdbc.sql("UPDATE workspaces SET deleted_at = :time WHERE id = :id")
                .param(
                        "time",
                        Timestamp.from(DocumentFixtures.CREATED_AT)
                )
                .param(
                        "id",
                        workspaceId
                )
                .update();

        // when & then
        request().andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("없는 문서와 다른 Workspace의 문서는 404이다")
    void findConfirmations_failure_missingOrForeignDocument() throws Exception {
        // given
        long otherWorkspace = fixtures.saveWorkspace();
        fixtures.join(
                otherWorkspace,
                readerId
        );

        // when & then
        mvc.perform(
                builder(
                        workspaceId,
                        Long.MAX_VALUE,
                        readerId
                )
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
        mvc.perform(
                builder(
                        otherWorkspace,
                        documentId,
                        readerId
                )
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("숫자가 아닌 경로 ID는 400이다")
    void findConfirmations_failure_invalidPath() throws Exception {
        // when & then
        mvc.perform(
                get(
                        PATH,
                        workspaceId,
                        "invalid"
                ).cookie(cookie(readerId))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(
                get(
                        PATH,
                        "invalid",
                        documentId
                ).cookie(cookie(readerId))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("Swagger는 대상 현황 DTO와 커서 및 쿠키 인증과 오류를 공개한다")
    void openApi_success_confirmationsContract() throws Exception {
        // given
        String operation = "$.paths['" + PATH + "'].get";
        String schema = "$.components.schemas.DocumentConfirmationsResponse";

        // when & then
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[*].accessTokenCookie").exists())
                .andExpect(
                        jsonPath(operation + ".parameters[*].name").value(
                                hasItems(
                                        "workspaceId",
                                        "documentId",
                                        "cursor",
                                        "size"
                                )
                        )
                )
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'authenticatedMember')]").doesNotExist())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").doesNotExist())
                .andExpect(
                        jsonPath(operation + ".responses['200'].content['application/json'].schema['$ref']")
                                .value("#/components/schemas/DocumentConfirmationsResponse")
                )
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists())
                .andExpect(jsonPath(operation + ".responses['404']").exists())
                .andExpect(
                        jsonPath(schema + ".required").value(
                                hasItems(
                                        "documentId",
                                        "confirmedCount",
                                        "pendingCount",
                                        "excludedCount",
                                        "confirmedByMe",
                                        "items",
                                        "nextCursor"
                                )
                        )
                )
                .andExpect(
                        jsonPath("$.components.schemas.DocumentConfirmationItemResponse.properties.state.enum").value(
                                hasItems(
                                        "CONFIRMED",
                                        "PENDING",
                                        "EXCLUDED"
                                )
                        )
                )
                .andExpect(
                        jsonPath("$.components.schemas.DocumentConfirmationItemResponse.required").value(
                                hasItems(
                                        "memberId",
                                        "nickname",
                                        "profileImageUrl",
                                        "confirmedAt",
                                        "state"
                                )
                        )
                );
    }

    private ResultActions request() throws Exception {
        return mvc.perform(
                builder(
                        workspaceId,
                        documentId,
                        readerId
                )
        );
    }

    private MockHttpServletRequestBuilder builder(
            long workspace,
            long document,
            long member
    ) {
        return get(
                PATH,
                workspace,
                document
        ).cookie(cookie(member));
    }

    private String cursor(
            DocumentConfirmationState state,
            long member
    ) {
        return DocumentConfirmationCursor.of(
                workspaceId,
                documentId,
                readerId,
                state,
                member
        )
                .encode();
    }

    private Cookie cookie(long memberId) {
        return new Cookie(
                "KNOT_ACCESS_TOKEN",
                tokens.issue(
                        AuthenticatedMember.of(
                                memberId,
                                "멤버",
                                null
                        )
                )
        );
    }
}
