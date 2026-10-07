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
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
class DocumentDetailAcceptanceTest {
    private static final String PATH = "/api/v1/workspaces/{workspaceId}/documents/{documentId}";
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private AuthTokenProvider tokens;
    private DocumentFixtures fixtures;
    private long authorId;
    private long readerId;
    private long workspaceId;
    private long transcriptId;
    private long recordingId;
    private long documentId;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE TABLE members, workspaces RESTART IDENTITY CASCADE")
                .update();
        fixtures = new DocumentFixtures(jdbc);
        authorId = fixtures.saveMember("작성자");
        readerId = fixtures.saveMember("새 가입자");
        workspaceId = fixtures.saveWorkspace();
        fixtures.join(
                workspaceId,
                authorId
        );
        recordingId = fixtures.saveRecording(
                workspaceId,
                authorId,
                1850999
        );
        transcriptId = fixtures.saveTranscript(recordingId);
        long jobId = fixtures.saveJob(
                transcriptId,
                "SUCCEEDED"
        );
        documentId = fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                jobId,
                "운영 정책"
        );
        fixtures.target(
                documentId,
                authorId,
                null
        );
        fixtures.join(
                workspaceId,
                readerId
        );
    }

    @Test
    @DisplayName("녹음에 참여하지 않은 새 멤버도 문서를 읽고 저장값은 변경되지 않는다")
    void findDocument_success_newMemberReadOnly() throws Exception {
        // given
        String before = fixtures.snapshot();

        // when
        ResultActions result = request(
                workspaceId,
                documentId,
                readerId
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.recordingSessionId").value(recordingId))
                .andExpect(jsonPath("$.sourceTranscriptId").value(transcriptId))
                .andExpect(jsonPath("$.topic").value("운영 정책"))
                .andExpect(jsonPath("$.title").value("문서 보관 정책"))
                .andExpect(jsonPath("$.content").value("# 읽기 전용 본문"))
                .andExpect(jsonPath("$.summary").value(nullValue()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-06T00:00:00Z"))
                .andExpect(jsonPath("$.archivedAt").value(nullValue()))
                .andExpect(jsonPath("$.recordingDurationSeconds").value(1850))
                .andExpect(jsonPath("$.myConfirmationState").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.confirmationSummary.confirmedCount").value(0))
                .andExpect(jsonPath("$.confirmationSummary.pendingCount").value(1))
                .andExpect(jsonPath("$.confirmationSummary.excludedCount").value(0));
        assertThat(fixtures.snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("미확인 대상은 PENDING이며 탈퇴한 미확인 대상은 제외로 집계한다")
    void findDocument_success_pendingAndExcluded() throws Exception {
        // given
        fixtures.target(
                documentId,
                readerId,
                null
        );
        fixtures.leave(
                workspaceId,
                authorId
        );

        // when & then
        request(
                workspaceId,
                documentId,
                readerId
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.myConfirmationState").value("PENDING"))
                .andExpect(jsonPath("$.confirmationSummary.pendingCount").value(1))
                .andExpect(jsonPath("$.confirmationSummary.excludedCount").value(1));
    }

    @Test
    @DisplayName("확인한 멤버는 보관 문서와 최초 확인 상태를 읽는다")
    void findDocument_success_archivedConfirmed() throws Exception {
        // given
        fixtures.target(
                documentId,
                readerId,
                DocumentFixtures.CREATED_AT.plusSeconds(30)
        );
        jdbc.sql("UPDATE document_confirmations SET confirmed_at = :time WHERE document_id = :id")
                .param(
                        "time",
                        Timestamp.from(DocumentFixtures.CREATED_AT.plusSeconds(30))
                )
                .param(
                        "id",
                        documentId
                )
                .update();
        jdbc.sql("UPDATE documents SET status = 'ARCHIVED', archived_at = :time, summary = 'AI 요약' WHERE id = :id")
                .param(
                        "time",
                        Timestamp.from(DocumentFixtures.CREATED_AT.plusSeconds(30))
                )
                .param(
                        "id",
                        documentId
                )
                .update();
        String before = fixtures.snapshot();

        // when & then
        request(
                workspaceId,
                documentId,
                readerId
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.summary").value("AI 요약"))
                .andExpect(jsonPath("$.archivedAt").value("2026-10-06T00:00:30Z"))
                .andExpect(jsonPath("$.myConfirmationState").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmationSummary.confirmedCount").value(2))
                .andExpect(jsonPath("$.confirmationSummary.pendingCount").value(0));
        assertThat(fixtures.snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("다른 주제의 실패 Job이 있어도 성공 문서는 즉시 조회한다")
    void findDocument_success_partialGeneration() throws Exception {
        // given
        fixtures.saveJob(
                transcriptId,
                "FAILED"
        );

        // when & then
        request(
                workspaceId,
                documentId,
                readerId
        ).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않은 상세 조회는 401이다")
    void findDocument_failure_unauthenticated() throws Exception {
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
    @DisplayName("비멤버는 존재하는 문서도 조회하지 못한다")
    void findDocument_failure_nonMember() throws Exception {
        // given
        long strangerId = fixtures.saveMember("외부인");

        // when & then
        request(
                workspaceId,
                documentId,
                strangerId
        ).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    @DisplayName("탈퇴자는 확인 대상이어도 문서를 조회하지 못한다")
    void findDocument_failure_departedMember() throws Exception {
        // given
        fixtures.leave(
                workspaceId,
                authorId
        );

        // when & then
        request(
                workspaceId,
                documentId,
                authorId
        ).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("삭제한 Workspace의 문서는 조회하지 못한다")
    void findDocument_failure_deletedWorkspace() throws Exception {
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
        request(
                workspaceId,
                documentId,
                readerId
        ).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("멤버인 다른 Workspace로 문서 ID를 제출하면 404다")
    void findDocument_failure_otherWorkspace() throws Exception {
        // given
        long otherWorkspace = fixtures.saveWorkspace();
        fixtures.join(
                otherWorkspace,
                readerId
        );

        // when & then
        request(
                otherWorkspace,
                documentId,
                readerId
        ).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("없는 문서는 404다")
    void findDocument_failure_missingDocument() throws Exception {
        // when & then
        request(
                workspaceId,
                Long.MAX_VALUE,
                readerId
        ).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("숫자가 아닌 문서 ID는 400이다")
    void findDocument_failure_invalidDocumentId() throws Exception {
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
    }

    @Test
    @DisplayName("숫자가 아닌 Workspace ID는 400이다")
    void findDocument_failure_invalidWorkspaceId() throws Exception {
        // when & then
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
    @DisplayName("Swagger는 상세 필드·3상태·오류·인증 계약을 공개한다")
    void openApi_success_documentDetailContract() throws Exception {
        // given
        String operation = "$.paths['" + PATH + "'].get";
        String properties = "$.components.schemas.DocumentDetailResponse.properties";

        // when & then
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[*].accessTokenCookie").exists())
                .andExpect(
                        jsonPath(operation + ".parameters[*].name").value(
                                hasItems(
                                        "workspaceId",
                                        "documentId"
                                )
                        )
                )
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'authenticatedMember')]").doesNotExist())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").doesNotExist())
                .andExpect(
                        jsonPath(operation + ".responses['200'].content['application/json'].schema['$ref']")
                                .value("#/components/schemas/DocumentDetailResponse")
                )
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists())
                .andExpect(jsonPath(operation + ".responses['404']").exists())
                .andExpect(jsonPath(properties + ".recordingSessionId").exists())
                .andExpect(jsonPath(properties + ".sourceTranscriptId").exists())
                .andExpect(
                        jsonPath(properties + ".myConfirmationState.enum").value(
                                hasItems(
                                        "PENDING",
                                        "CONFIRMED",
                                        "NOT_REQUIRED"
                                )
                        )
                )
                .andExpect(
                        jsonPath(properties + ".status.enum").value(
                                hasItems(
                                        "DRAFT",
                                        "ARCHIVED"
                                )
                        )
                )
                .andExpect(jsonPath(properties + ".confirmedByMe").doesNotExist())
                .andExpect(
                        jsonPath("$.components.schemas.DocumentDetailResponse.required").value(
                                hasItems(
                                        "id",
                                        "recordingSessionId",
                                        "content",
                                        "myConfirmationState",
                                        "confirmationSummary"
                                )
                        )
                );
    }

    private ResultActions request(
            long currentWorkspaceId,
            long currentDocumentId,
            long currentMemberId
    ) throws Exception {
        return mvc.perform(
                get(
                        PATH,
                        currentWorkspaceId,
                        currentDocumentId
                ).cookie(cookie(currentMemberId))
        );
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
