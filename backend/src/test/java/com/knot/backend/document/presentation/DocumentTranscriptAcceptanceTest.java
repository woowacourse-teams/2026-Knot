package com.knot.backend.document.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.recording.domain.TranscriptSegment;
import com.knot.backend.recording.domain.TranscriptSegmentRepository;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.List;
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

@Tag("acceptance")
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
class DocumentTranscriptAcceptanceTest {

    private static final String PATH = "/api/v1/workspaces/{workspaceId}/documents/{documentId}/transcript";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private AuthTokenProvider tokens;
    @Autowired
    private TranscriptSegmentRepository segments;
    private DocumentFixtures fixtures;
    private long authorId;
    private long readerId;
    private long workspaceId;
    private long transcriptId;
    private long documentId;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE TABLE members, workspaces RESTART IDENTITY CASCADE")
                .update();
        fixtures = new DocumentFixtures(jdbc);
        workspaceId = fixtures.saveWorkspace();
        authorId = fixtures.saveMember("녹음자");
        readerId = fixtures.saveMember("후발 멤버");
        fixtures.join(
                workspaceId,
                authorId
        );
        long recordingId = fixtures.saveRecording(
                workspaceId,
                authorId,
                1850999
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
                authorId,
                null
        );
        fixtures.join(
                workspaceId,
                readerId
        );
        segments.saveAll(
                List.of(
                        TranscriptSegment.create(
                                transcriptId,
                                2,
                                1200,
                                4600L,
                                2,
                                "세 번째"
                        ),
                        TranscriptSegment.create(
                                transcriptId,
                                0,
                                0,
                                null,
                                null,
                                "첫 번째"
                        ),
                        TranscriptSegment.create(
                                transcriptId,
                                1,
                                1200,
                                null,
                                1,
                                "두 번째"
                        )
                )
        );
    }

    @Test
    @DisplayName("녹음 비참여·후발 멤버도 CSRF 헤더 없이 전체 원문과 구간을 읽는다")
    void findTranscript_success_newMemberReadOnly() throws Exception {
        // given
        String before = snapshot();

        // when
        ResultActions result = request(
                workspaceId,
                documentId,
                readerId
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.transcriptId").value(transcriptId))
                .andExpect(jsonPath("$.isPartial").value(false))
                .andExpect(jsonPath("$.recordingDurationSeconds").value(1850))
                .andExpect(jsonPath("$.transcriptText").value("전체 녹음 원문"))
                .andExpect(jsonPath("$.segments.length()").value(3))
                .andExpect(jsonPath("$.segments[0].startMillis").value(0))
                .andExpect(jsonPath("$.segments[0].endMillis").value(nullValue()))
                .andExpect(jsonPath("$.segments[0].speakerNumber").value(nullValue()))
                .andExpect(jsonPath("$.segments[1].text").value("두 번째"))
                .andExpect(jsonPath("$.segments[2].speakerNumber").value(2))
                .andExpect(jsonPath("$.segments[2].endMillis").value(4600))
                .andExpect(jsonPath("$.status").doesNotExist());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("보관 문서와 다른 주제의 만료 실패 Job은 원문 조회에 영향을 주지 않는다")
    void findTranscript_success_archivedAndExpiredOtherJob() throws Exception {
        // given
        jdbc.sql("UPDATE documents SET status = 'ARCHIVED', archived_at = :time WHERE id = :id")
                .param(
                        "time",
                        Timestamp.from(DocumentFixtures.CREATED_AT.plusSeconds(60))
                )
                .param(
                        "id",
                        documentId
                )
                .update();
        fixtures.saveJob(
                transcriptId,
                "FAILED",
                DocumentFixtures.CREATED_AT.minusSeconds(9 * 86400),
                DocumentFixtures.CREATED_AT.minusSeconds(8 * 86400)
        );
        String before = snapshot();

        // when & then
        request(
                workspaceId,
                documentId,
                readerId
        ).andExpect(status().isOk());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("미로그인은 401이다")
    void findTranscript_failure_unauthenticated() throws Exception {
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
    @DisplayName("비멤버는 403이다")
    void findTranscript_failure_nonMember() throws Exception {
        // given
        long outsider = fixtures.saveMember("외부인");

        // when & then
        request(
                workspaceId,
                documentId,
                outsider
        ).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("탈퇴한 확인 대상도 원문을 조회할 수 없다")
    void findTranscript_failure_departedMember() throws Exception {
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
    @DisplayName("삭제된 Workspace는 조회할 수 없다")
    void findTranscript_failure_deletedWorkspace() throws Exception {
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
    @DisplayName("다른 Workspace로 문서를 조회하면 원문 없음 404다")
    void findTranscript_failure_otherWorkspace() throws Exception {
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
                .andExpect(jsonPath("$.code").value("TRANSCRIPT_NOT_FOUND"));
    }

    @Test
    @DisplayName("없는 문서는 원문 없음 404다")
    void findTranscript_failure_missingDocument() throws Exception {
        // when & then
        request(
                workspaceId,
                Long.MAX_VALUE,
                readerId
        ).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSCRIPT_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid"})
    @DisplayName("유효하지 않은 문서 ID는 400이다")
    void findTranscript_failure_invalidDocumentId(String invalidId) throws Exception {
        // when & then
        mvc.perform(
                get(
                        PATH,
                        workspaceId,
                        invalidId
                ).cookie(cookie(readerId))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("구간 누락을 빈 성공 응답으로 숨기지 않고 내부 내용을 노출하지 않는다")
    void findTranscript_failure_incompleteStoredTranscript() throws Exception {
        // given
        jdbc.sql("DELETE FROM transcript_segments WHERE transcript_id = :id")
                .param(
                        "id",
                        transcriptId
                )
                .update();

        // when & then
        request(
                workspaceId,
                documentId,
                readerId
        ).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSCRIPT_DATA"))
                .andExpect(jsonPath("$.message").value("문서 원문을 불러올 수 없습니다"))
                .andExpect(jsonPath("$.transcriptText").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    @DisplayName("전체 텍스트 누락도 원문 데이터 오류로 응답하고 내부 내용을 노출하지 않는다")
    void findTranscript_failure_blankStoredTranscript(String text) throws Exception {
        // given
        jdbc.sql("UPDATE transcripts SET content = :text WHERE id = :id")
                .param(
                        "text",
                        text
                )
                .param(
                        "id",
                        transcriptId
                )
                .update();

        // when & then
        request(
                workspaceId,
                documentId,
                readerId
        ).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSCRIPT_DATA"))
                .andExpect(jsonPath("$.message").value("문서 원문을 불러올 수 없습니다"))
                .andExpect(jsonPath("$.transcriptText").doesNotExist())
                .andExpect(jsonPath("$.segments").doesNotExist());
    }

    @Test
    @DisplayName("OpenAPI는 실제 GET·필수 시작 시각과 nullable 종료·화자를 설명한다")
    void findTranscript_success_openApi() throws Exception {
        // when & then
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/workspaces/{workspaceId}/documents/{documentId}/transcript'].get.responses['200']"
                        ).exists()
                )
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/workspaces/{workspaceId}/documents/{documentId}/transcript'].get.responses['404'].description"
                        ).value(containsString("TRANSCRIPT_NOT_FOUND"))
                )
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/workspaces/{workspaceId}/documents/{documentId}/transcript'].get.responses['500'].description"
                        ).value(containsString("INVALID_TRANSCRIPT_DATA"))
                )
                .andExpect(
                        jsonPath("$.components.schemas.DocumentTranscriptResponse.required").value(
                                hasItems(
                                        "transcriptId",
                                        "isPartial",
                                        "recordingDurationSeconds",
                                        "transcriptText",
                                        "segments"
                                )
                        )
                )
                .andExpect(
                        jsonPath("$.components.schemas.DocumentTranscriptSegmentResponse.required").value(
                                hasItems(
                                        "startMillis",
                                        "text"
                                )
                        )
                )
                .andExpect(
                        jsonPath("$.components.schemas.DocumentTranscriptResponse.properties.segments.minItems")
                                .value(1)
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

    private String snapshot() {
        return fixtures.snapshot() + jdbc.sql("""
                SELECT coalesce((SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM transcripts t), '[]')
                    || coalesce((SELECT jsonb_agg(to_jsonb(s) ORDER BY id)::text FROM transcript_segments s), '[]')
                """)
                .query(String.class)
                .single();
    }
}
