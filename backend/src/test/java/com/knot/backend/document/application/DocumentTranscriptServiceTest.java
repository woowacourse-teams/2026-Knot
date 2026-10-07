package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.application.dto.result.DocumentTranscriptSnapshot;
import com.knot.backend.document.application.dto.result.DocumentTranscriptResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.recording.application.dto.result.TranscriptSegmentResult;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentTranscriptServiceTest {

    @Mock
    private WorkspaceMemberRepository members;
    @Mock
    private DocumentTranscriptQuery query;
    private DocumentTranscriptService service;

    @BeforeEach
    void setUp() {
        service = new DocumentTranscriptService(
                members,
                query
        );
    }

    @Test
    @DisplayName("현재 멤버는 문서에 연결된 전체 원문과 저장된 구간을 읽는다")
    void find_success() {
        // given
        allowMember();
        TranscriptSegmentResult segment = new TranscriptSegmentResult(
                1200,
                null,
                null,
                "회의 발언"
        );
        when(
                query.find(
                        1,
                        3
                )
        ).thenReturn(
                Optional.of(
                        new DocumentTranscriptSnapshot(
                                81,
                                1850999,
                                "전체 원문",
                                List.of(segment)
                        )
                )
        );

        // when
        DocumentTranscriptResult result = service.find(
                1,
                2,
                3
        );

        // then
        assertThat(result.transcriptId()).isEqualTo(81);
        assertThat(result.recordingDurationSeconds()).isEqualTo(1850);
        assertThat(result.transcriptText()).isEqualTo("전체 원문");
        assertThat(result.segments()).containsExactly(segment);
    }

    @Test
    @DisplayName("비멤버는 원문 존재 여부를 조회하지 않고 거절한다")
    void find_failure_accessDenied() {
        // when & then
        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        3
                )
        ).isInstanceOf(WorkspaceException.class)
                .extracting(
                        exception -> ((WorkspaceException) exception).getErrorCode()
                                .getCode()
                )
                .isEqualTo("WORKSPACE_ACCESS_DENIED");
        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("조회 범위에 문서 또는 연결 원문이 없으면 TRANSCRIPT_NOT_FOUND다")
    void find_failure_missingTranscript() {
        // given
        allowMember();
        when(
                query.find(
                        1,
                        3
                )
        ).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        3
                )
        ).isInstanceOf(DocumentException.class)
                .extracting(exception -> ((DocumentException) exception).getErrorCode())
                .isEqualTo(DocumentErrorCode.TRANSCRIPT_NOT_FOUND);
    }

    @ParameterizedTest
    @CsvSource({"0,2,3", "1,0,3", "1,2,-1"})
    @DisplayName("유효하지 않은 식별자는 저장소 조회 전에 거절한다")
    void find_failure_invalidId(
            long workspaceId,
            long memberId,
            long documentId
    ) {
        // when & then
        assertThatThrownBy(
                () -> service.find(
                        workspaceId,
                        memberId,
                        documentId
                )
        ).isInstanceOf(DocumentException.class)
                .extracting(exception -> ((DocumentException) exception).getErrorCode())
                .isEqualTo(DocumentErrorCode.INVALID_PARAMETER);
        verifyNoInteractions(
                members,
                query
        );
    }

    @Test
    @DisplayName("발화 원문에 구간이 없으면 가짜 시각 또는 빈 성공 응답을 만들지 않는다")
    void find_failure_missingSegments() {
        // given
        allowMember();
        when(
                query.find(
                        1,
                        3
                )
        ).thenReturn(
                Optional.of(
                        new DocumentTranscriptSnapshot(
                                81,
                                1850999,
                                "전체 원문",
                                List.of()
                        )
                )
        );

        // when & then
        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        3
                )
        ).isInstanceOf(DocumentException.class)
                .extracting(
                        exception -> ((DocumentException) exception).getErrorCode()
                                .getCode()
                )
                .isEqualTo("INVALID_TRANSCRIPT_DATA");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("전체 원문 누락을 정상 성공 응답으로 숨기지 않는다")
    void find_failure_blankTranscriptText(String text) {
        // given
        allowMember();
        when(
                query.find(
                        1,
                        3
                )
        ).thenReturn(
                Optional.of(
                        new DocumentTranscriptSnapshot(
                                81,
                                1000,
                                text,
                                List.of(
                                        new TranscriptSegmentResult(
                                                0,
                                                null,
                                                null,
                                                "발언"
                                        )
                                )
                        )
                )
        );

        // when & then
        assertThatThrownBy(
                () -> service.find(
                        1,
                        2,
                        3
                )
        ).isInstanceOf(DocumentException.class)
                .extracting(
                        exception -> ((DocumentException) exception).getErrorCode()
                                .getCode()
                )
                .isEqualTo("INVALID_TRANSCRIPT_DATA");
    }

    private void allowMember() {
        when(
                members.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
    }
}
