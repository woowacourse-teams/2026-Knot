package com.knot.backend.document.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Tag("acceptance")
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
class DocumentConfirmationAcceptanceTest {
    private static final String PATH = "/api/v1/workspaces/{workspaceId}/documents/{documentId}/confirmations/me";
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private AuthTokenProvider tokens;
    private DocumentFixtures fixtures;
    private long workspaceId;
    private long targetId;
    private long readerId;
    private long documentId;
    private long transcriptId;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE TABLE members, workspaces RESTART IDENTITY CASCADE")
                .update();
        fixtures = new DocumentFixtures(jdbc);
        targetId = fixtures.saveMember("대상");
        readerId = fixtures.saveMember("이후 가입자");
        workspaceId = fixtures.saveWorkspace();
        fixtures.join(
                workspaceId,
                targetId
        );
        fixtures.join(
                workspaceId,
                readerId
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
    }

    @Test
    @DisplayName("본문 없는 PUT으로 확인하고 마지막 필수 대상이면 보관한다")
    void confirmDocument_success() throws Exception {
        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        documentId,
                        targetId
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.confirmedAt").isString())
                .andExpect(jsonPath("$.documentStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedAt").isString())
                .andExpect(jsonPath("$.confirmationSummary.confirmedCount").value(1))
                .andExpect(jsonPath("$.confirmationSummary.pendingCount").value(0))
                .andExpect(jsonPath("$.confirmationSummary.excludedCount").value(0));
        assertThat(storedConfirmationTime()).isNotNull();
    }

    @Test
    @DisplayName("남은 대상이 있으면 DRAFT를 반환하고 반복 확인은 최초 시각을 유지한다")
    void confirmDocument_success_repeatedRequest() throws Exception {
        // given
        fixtures.target(
                documentId,
                readerId,
                null
        );
        mvc.perform(
                request(
                        workspaceId,
                        documentId,
                        targetId
                )
        )
                .andExpect(status().isOk());
        Instant first = storedConfirmationTime();

        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        documentId,
                        targetId
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentStatus").value("DRAFT"))
                .andExpect(jsonPath("$.archivedAt").value(nullValue()))
                .andExpect(jsonPath("$.confirmationSummary.confirmedCount").value(1))
                .andExpect(jsonPath("$.confirmationSummary.pendingCount").value(1));
        assertThat(storedConfirmationTime()).isEqualTo(first);
    }

    @Test
    @DisplayName("다른 주제 실패·실행 중·실패 작업 정리는 성공 문서의 확인 시각에 영향을 주지 않는다")
    void confirmDocument_success_independentJobs() throws Exception {
        // given
        long failedJob = fixtures.saveJob(
                transcriptId,
                "FAILED"
        );
        fixtures.saveJob(
                transcriptId,
                "RUNNING"
        );
        mvc.perform(
                request(
                        workspaceId,
                        documentId,
                        targetId
                )
        )
                .andExpect(status().isOk());
        String before = fixtures.snapshot();
        jdbc.sql("DELETE FROM document_generation_jobs WHERE id = :id")
                .param(
                        "id",
                        failedJob
                )
                .update();

        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        documentId,
                        targetId
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentStatus").value("ARCHIVED"));
        assertThat(fixtures.snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("확인 비대상인 이후 가입자는 409이며 저장 내용은 유지한다")
    void confirmDocument_failure_notRequired() throws Exception {
        // given
        String before = fixtures.snapshot();

        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        documentId,
                        readerId
                )
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_NOT_REQUIRED"));
        assertThat(fixtures.snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("유효한 CSRF가 있어도 로그인하지 않으면 401이다")
    void confirmDocument_failure_unauthenticated() throws Exception {
        // when & then
        mvc.perform(
                withCsrf(
                        put(
                                PATH,
                                workspaceId,
                                documentId
                        )
                )
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("CSRF가 없거나 틀리면 403이며 확인을 저장하지 않는다")
    void confirmDocument_failure_csrf() throws Exception {
        // given
        String before = fixtures.snapshot();

        // when & then
        mvc.perform(
                put(
                        PATH,
                        workspaceId,
                        documentId
                ).cookie(cookie(targetId))
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(
                request(
                        workspaceId,
                        documentId,
                        targetId
                ).header(
                        "X-XSRF-TOKEN",
                        "invalid"
                )
        )
                .andExpect(status().isForbidden());
        assertThat(fixtures.snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("비멤버와 탈퇴자는 403이며 확인을 저장하지 않는다")
    void confirmDocument_failure_workspaceAccess() throws Exception {
        // given
        long outsider = fixtures.saveMember("외부인");
        fixtures.leave(
                workspaceId,
                targetId
        );
        String before = fixtures.snapshot();

        // when & then
        mvc.perform(
                request(
                        workspaceId,
                        documentId,
                        outsider
                )
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
        mvc.perform(
                request(
                        workspaceId,
                        documentId,
                        targetId
                )
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
        assertThat(fixtures.snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("다른 Workspace의 문서와 없는 문서는 404이다")
    void confirmDocument_failure_foreignDocument() throws Exception {
        // given
        long other = fixtures.saveWorkspace();
        fixtures.join(
                other,
                targetId
        );

        // when & then
        mvc.perform(
                request(
                        other,
                        documentId,
                        targetId
                )
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
        mvc.perform(
                request(
                        workspaceId,
                        Long.MAX_VALUE,
                        targetId
                )
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("경로 ID 형식 오류는 400이다")
    void confirmDocument_failure_invalidPath() throws Exception {
        // when & then
        mvc.perform(
                withCsrf(
                        put(
                                PATH,
                                workspaceId,
                                "invalid"
                        ).cookie(cookie(targetId))
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("Swagger는 PUT·빈 본문·CSRF·응답 필드와 409 계약을 공개한다")
    void openApi_success_confirmationContract() throws Exception {
        // given
        String operation = "$.paths['" + PATH + "'].put";

        // when & then
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".requestBody").doesNotExist())
                .andExpect(jsonPath(operation + ".security[*].accessTokenCookie").exists())
                .andExpect(
                        jsonPath(operation + ".parameters[?(@.name == 'X-XSRF-TOKEN')].required").value(hasItems(true))
                )
                .andExpect(jsonPath(operation + ".responses['409']").exists())
                .andExpect(
                        jsonPath(operation + ".responses['200'].content['application/json'].schema['$ref']")
                                .value("#/components/schemas/DocumentConfirmationResponse")
                )
                .andExpect(
                        jsonPath("$.components.schemas.DocumentConfirmationResponse.required").value(
                                hasItems(
                                        "documentId",
                                        "confirmedAt",
                                        "documentStatus",
                                        "archivedAt",
                                        "confirmationSummary"
                                )
                        )
                );
    }

    private MockHttpServletRequestBuilder request(
            long workspace,
            long document,
            long member
    ) throws Exception {
        return withCsrf(
                put(
                        PATH,
                        workspace,
                        document
                ).cookie(cookie(member))
        ).contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) throws Exception {
        Cookie csrf = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");
        return builder.cookie(csrf)
                .header(
                        "X-XSRF-TOKEN",
                        csrf.getValue()
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

    private Instant storedConfirmationTime() {
        return jdbc
                .sql("SELECT confirmed_at FROM document_confirmations WHERE document_id = :id AND member_id = :member")
                .param(
                        "id",
                        documentId
                )
                .param(
                        "member",
                        targetId
                )
                .query(Instant.class)
                .single();
    }
}
