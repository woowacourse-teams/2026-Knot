package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.application.dto.result.DocumentGenerationInputResult;
import com.knot.backend.document.application.dto.result.DocumentGenerationJobRetryResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationExecutionRequest;
import com.knot.backend.document.domain.DocumentGenerationExecutionRequestRepository;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class DocumentGenerationJobRetryServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T00:00:00Z");
    private static final Instant NOW = CREATED_AT.plusSeconds(120);

    private WorkspaceRepository workspaces;
    private WorkspaceMemberRepository members;
    private DocumentGenerationJobRepository jobs;
    private DocumentGenerationInputQuery inputs;
    private DocumentGenerationExecutionRequestRepository requests;
    private DocumentGenerationJobRetryService service;
    private DocumentGenerationJob job;

    @BeforeEach
    void setUp() {
        workspaces = mock(WorkspaceRepository.class);
        members = mock(WorkspaceMemberRepository.class);
        jobs = mock(DocumentGenerationJobRepository.class);
        inputs = mock(DocumentGenerationInputQuery.class);
        requests = mock(DocumentGenerationExecutionRequestRepository.class);
        service = new DocumentGenerationJobRetryService(
                workspaces,
                members,
                jobs,
                inputs,
                requests,
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                )
        );
        job = DocumentGenerationJob.queue(
                3,
                CREATED_AT
        );
        job.recordFailure(CREATED_AT.plusSeconds(60));
        when(workspaces.findByIdForUpdate(1L)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "문서 팀",
                                CREATED_AT
                        )
                )
        );
        when(
                members.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
        when(
                jobs.findByWorkspaceIdAndIdForUpdate(
                        1,
                        88
                )
        ).thenReturn(Optional.of(job));
        when(
                inputs.findForUpdate(
                        1,
                        3
                )
        ).thenReturn(
                Optional.of(
                        new DocumentGenerationInputResult(
                                3,
                                "저장된 원문"
                        )
                )
        );
    }

    @Test
    @DisplayName("사용자 재시도는 같은 Job·증가한 회차로 영속 접수하고 QUEUED 결과를 반환한다")
    void retry_success() {
        // when
        DocumentGenerationJobRetryResult result = service.retry(
                1,
                2,
                88
        );

        // then
        assertThat(result.jobId()).isEqualTo(88);
        assertThat(result.status()).isEqualTo(DocumentGenerationJobStatus.QUEUED);
        assertThat(result.attemptCount()).isEqualTo(2);
        ArgumentCaptor<DocumentGenerationExecutionRequest> captor = ArgumentCaptor
                .forClass(DocumentGenerationExecutionRequest.class);
        verify(requests).save(captor.capture());
        assertThat(
                captor.getValue()
                        .getJobId()
        ).isEqualTo(88);
        assertThat(
                captor.getValue()
                        .getAttemptCount()
        ).isEqualTo(2);
        assertThat(
                captor.getValue()
                        .getAcceptedAt()
        ).isEqualTo(NOW);
        verify(jobs).flush();
        verify(requests).flush();
    }

    @Test
    @DisplayName("삭제·없는 Workspace는 Job을 조회하지 않고 접근을 거절한다")
    void retry_failure_missingWorkspace() {
        // given
        when(workspaces.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> service.retry(
                        1,
                        2,
                        88
                )
        ).isInstanceOf(WorkspaceException.class);
        verifyNoInteractions(
                jobs,
                inputs,
                requests
        );
    }

    @Test
    @DisplayName("현재 멤버가 아니면 Job 정보에 접근하지 않는다")
    void retry_failure_nonMember() {
        // given
        when(
                members.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(false);

        // when & then
        assertThatThrownBy(
                () -> service.retry(
                        1,
                        2,
                        88
                )
        ).isInstanceOf(WorkspaceException.class);
        verifyNoInteractions(
                jobs,
                inputs,
                requests
        );
    }

    @Test
    @DisplayName("Workspace 범위 안에 없는 Job은 404 계약 오류다")
    void retry_failure_missingJob() {
        // given
        when(
                jobs.findByWorkspaceIdAndIdForUpdate(
                        1,
                        88
                )
        ).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> service.retry(
                        1,
                        2,
                        88
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.DOCUMENT_GENERATION_JOB_NOT_FOUND.getMessage());
        verifyNoInteractions(
                inputs,
                requests
        );
    }

    @Test
    @DisplayName("입력이 없으면 상태·횟수를 유지하고 접수하지 않는다")
    void retry_failure_missingInput() {
        // given
        when(
                inputs.findForUpdate(
                        1,
                        3
                )
        ).thenReturn(Optional.empty());

        // when & then
        assertDeniedWithoutRequest();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    @DisplayName("사용 가능한 텍스트가 없는 원문은 접수하지 않는다")
    void retry_failure_blankInput(String content) {
        // given
        when(
                inputs.findForUpdate(
                        1,
                        3
                )
        ).thenReturn(
                Optional.of(
                        new DocumentGenerationInputResult(
                                3,
                                content
                        )
                )
        );

        // when & then
        assertDeniedWithoutRequest();
    }

    @Test
    @DisplayName("QUEUED로 바뀐 같은 Job의 후속 요청은 추가 접수하지 않는다")
    void retry_failure_alreadyQueued() {
        // given
        job.retryByUser(NOW);

        // when & then
        assertThatThrownBy(
                () -> service.retry(
                        1,
                        2,
                        88
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.RETRY_NOT_ALLOWED.getMessage());
        assertThat(job.getAttemptCount()).isEqualTo(2);
        verifyNoInteractions(requests);
    }

    @Test
    @DisplayName("접수 저장 장애는 숨기지 않고 트랜잭션 경계 밖으로 전달한다")
    void retry_failure_requestPersistence() {
        // given
        doThrow(new IllegalStateException("접수 저장 장애")).when(requests)
                .save(any(DocumentGenerationExecutionRequest.class));

        // when & then
        assertThatThrownBy(
                () -> service.retry(
                        1,
                        2,
                        88
                )
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("접수 저장 장애");
    }

    @Test
    @DisplayName("양수가 아닌 식별자는 저장소 호출 전에 거절한다")
    void retry_failure_invalidIdentifiers() {
        // when & then
        assertThatThrownBy(
                () -> service.retry(
                        0,
                        2,
                        88
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> service.retry(
                        1,
                        0,
                        88
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> service.retry(
                        1,
                        2,
                        0
                )
        ).isInstanceOf(DocumentException.class);
        verifyNoInteractions(
                workspaces,
                members,
                jobs,
                inputs,
                requests
        );
    }

    private void assertDeniedWithoutRequest() {
        assertThatThrownBy(
                () -> service.retry(
                        1,
                        2,
                        88
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.RETRY_NOT_ALLOWED.getMessage());
        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.FAILED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        verifyNoInteractions(requests);
    }
}
