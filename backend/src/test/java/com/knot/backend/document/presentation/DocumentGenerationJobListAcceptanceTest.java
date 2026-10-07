package com.knot.backend.document.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.document.domain.DocumentGenerationJobCursor;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("acceptance")
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
class DocumentGenerationJobListAcceptanceTest {
    private static final String PATH = "/api/v1/workspaces/{workspaceId}/document-generation-jobs";
    private static final Instant NOW = DocumentFixtures.CREATED_AT.plusSeconds(8 * 24 * 60 * 60);
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private AuthTokenProvider tokens;
    @MockitoBean
    private Clock clock;
    private DocumentFixtures fixtures;
    private long memberId;
    private long workspaceId;
    private long recordingId;
    private long transcriptId;
    private long queuedId;
    private long runningId;
    private long failedId;
    private long succeededId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        jdbc.sql("TRUNCATE TABLE members, workspaces RESTART IDENTITY CASCADE")
                .update();
        fixtures = new DocumentFixtures(jdbc);
        memberId = fixtures.saveMember("조회 멤버");
        workspaceId = fixtures.saveWorkspace();
        fixtures.join(
                workspaceId,
                memberId
        );
        recordingId = fixtures.saveRecording(
                workspaceId,
                memberId,
                120000
        );
        transcriptId = fixtures.saveTranscript(recordingId);
        queuedId = fixtures.saveJob(
                transcriptId,
                "QUEUED"
        );
        runningId = fixtures.saveJob(
                transcriptId,
                "RUNNING"
        );
        succeededId = fixtures.saveJob(
                transcriptId,
                "SUCCEEDED"
        );
        failedId = fixtures.saveJob(
                transcriptId,
                "FAILED",
                DocumentFixtures.CREATED_AT,
                NOW.minusSeconds(60)
        );
        fixtures.saveJob(
                transcriptId,
                "FAILED"
        );
    }

    @Test
    @DisplayName("GET은 진행·기한 내 실패만 반환하고 만료 행과 성공 문서를 삭제하지 않는다")
    void findDocumentGenerationJobs_success_readOnly() throws Exception {
        // given
        long documentId = fixtures.saveDocument(
                workspaceId,
                recordingId,
                transcriptId,
                succeededId,
                "운영 정책"
        );
        jdbc.sql("UPDATE recording_sessions SET title = '주간 회의' WHERE id = :id")
                .param(
                        "id",
                        recordingId
                )
                .update();
        String before = fixtures.snapshot();

        // when
        ResultActions result = request(
                workspaceId,
                memberId
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].jobId").value(failedId))
                .andExpect(jsonPath("$.items[1].jobId").value(runningId))
                .andExpect(jsonPath("$.items[2].jobId").value(queuedId))
                .andExpect(jsonPath("$.items[0].recordingSessionId").value(recordingId))
                .andExpect(jsonPath("$.items[0].recordingTitle").value("주간 회의"))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].createdAt").value(DocumentFixtures.CREATED_AT.toString()))
                .andExpect(
                        jsonPath("$.items[0].updatedAt").value(
                                NOW.minusSeconds(60)
                                        .toString()
                        )
                )
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        mvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/documents/{documentId}",
                        workspaceId,
                        documentId
                ).cookie(cookie(memberId))
        )
                .andExpect(status().isOk());
        assertThat(fixtures.snapshot()).isEqualTo(before);
        assertThat(
                jdbc.sql("SELECT count(*) FROM document_generation_jobs")
                        .query(Long.class)
                        .single()
        ).isEqualTo(5);
    }

    @Test
    @DisplayName("응답 커서로 중복 없이 끝까지 조회하며 마지막 커서는 null이다")
    void findDocumentGenerationJobs_success_pagination() throws Exception {
        // given
        String body = mvc.perform(
                get(
                        PATH,
                        workspaceId
                ).cookie(cookie(memberId))
                        .param(
                                "size",
                                "2"
                        )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = JsonPath.read(
                body,
                "$.nextCursor"
        );

        // when & then
        mvc.perform(
                get(
                        PATH,
                        workspaceId
                ).cookie(cookie(memberId))
                        .param(
                                "size",
                                "2"
                        )
                        .param(
                                "cursor",
                                cursor
                        )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].jobId").value(queuedId))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("새로 합류한 현재 멤버는 원래 녹음 참여와 관계없이 조회하고 제목 미입력은 null이다")
    void findDocumentGenerationJobs_success_newMember() throws Exception {
        // given
        long newcomer = fixtures.saveMember("새 멤버");
        fixtures.join(
                workspaceId,
                newcomer
        );

        // when & then
        request(
                workspaceId,
                newcomer
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].recordingTitle").value(nullValue()));
    }

    @Test
    @DisplayName("작업이 없는 Workspace는 빈 배열과 null 커서를 반환한다")
    void findDocumentGenerationJobs_success_emptyWorkspace() throws Exception {
        // given
        long emptyWorkspace = fixtures.saveWorkspace();
        fixtures.join(
                emptyWorkspace,
                memberId
        );

        // when & then
        request(
                emptyWorkspace,
                memberId
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("인증 쿠키가 없으면 401이다")
    void findDocumentGenerationJobs_failure_unauthenticated() throws Exception {
        // when & then
        mvc.perform(
                get(
                        PATH,
                        workspaceId
                )
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("비멤버 또는 탈퇴한 요청자는 403이다")
    void findDocumentGenerationJobs_failure_nonMemberOrLeft() throws Exception {
        // given
        long outsider = fixtures.saveMember("비멤버");
        fixtures.leave(
                workspaceId,
                memberId
        );

        // when & then
        request(
                workspaceId,
                outsider
        ).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
        request(
                workspaceId,
                memberId
        ).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid!"})
    @DisplayName("잘못된 커서는 400이다")
    void findDocumentGenerationJobs_failure_invalidCursor(String cursor) throws Exception {
        // when & then
        mvc.perform(
                get(
                        PATH,
                        workspaceId
                ).cookie(cookie(memberId))
                        .param(
                                "cursor",
                                cursor
                        )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("다른 Workspace 또는 Member에서 발급받은 커서는 400이다")
    void findDocumentGenerationJobs_failure_cursorScope() throws Exception {
        // given
        String workspaceCursor = DocumentGenerationJobCursor.of(
                workspaceId + 1,
                memberId,
                NOW,
                queuedId
        )
                .encode();
        String memberCursor = DocumentGenerationJobCursor.of(
                workspaceId,
                memberId + 1,
                NOW,
                queuedId
        )
                .encode();

        // when & then
        mvc.perform(
                get(
                        PATH,
                        workspaceId
                ).cookie(cookie(memberId))
                        .param(
                                "cursor",
                                workspaceCursor
                        )
        )
                .andExpect(status().isBadRequest());
        mvc.perform(
                get(
                        PATH,
                        workspaceId
                ).cookie(cookie(memberId))
                        .param(
                                "cursor",
                                memberCursor
                        )
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Swagger는 목록·크기·쿠키 인증을 명시하고 변경 요청용 토큰을 요구하지 않는다")
    void openApi_success_contract() throws Exception {
        // given
        String operation = "$.paths['" + PATH + "'].get";

        // when & then
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".security[*].accessTokenCookie").exists())
                .andExpect(
                        jsonPath(operation + ".parameters[*].name").value(
                                hasItems(
                                        "workspaceId",
                                        "cursor",
                                        "size"
                                )
                        )
                )
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'size')].schema.default").value(hasItems(20)))
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'X-XSRF-TOKEN')]").doesNotExist())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'authenticatedMember')]").doesNotExist())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists())
                .andExpect(
                        jsonPath("$.components.schemas.DocumentGenerationJobItemResponse.properties.recordingTitle")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.components.schemas.DocumentGenerationJobItemResponse.properties.status.enum").value(
                                contains(
                                        "QUEUED",
                                        "RUNNING",
                                        "FAILED"
                                )
                        )
                );
    }

    private ResultActions request(
            long requestedWorkspaceId,
            long requestedMemberId
    ) throws Exception {
        return mvc.perform(
                get(
                        PATH,
                        requestedWorkspaceId
                ).cookie(cookie(requestedMemberId))
        );
    }

    private Cookie cookie(long requestedMemberId) {
        return new Cookie(
                "KNOT_ACCESS_TOKEN",
                tokens.issue(
                        AuthenticatedMember.of(
                                requestedMemberId,
                                "멤버",
                                null
                        )
                )
        );
    }
}
