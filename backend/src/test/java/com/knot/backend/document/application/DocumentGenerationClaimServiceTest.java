package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationBatchRepository;
import com.knot.backend.document.domain.DocumentGenerationJob;
import com.knot.backend.document.domain.DocumentGenerationJobRepository;
import com.knot.backend.global.config.DocumentGenerationWorkerProperties;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentGenerationClaimServiceTest {
    private static final long WORKSPACE_ID = 11;
    private static final long JOB_ID = 12;
    private static final long TRANSCRIPT_ID = 13;
    private static final long BATCH_ID = 14;
    private static final Instant NOW = Instant.parse("2026-10-09T09:00:00Z");

    @Mock
    private WorkspaceRepository workspaces;
    @Mock
    private DocumentGenerationJobRepository jobs;
    @Mock
    private DocumentGenerationInputQuery inputs;
    @Mock
    private DocumentGenerationBatchRepository batches;
    @Mock
    private DocumentGenerationWorkerProperties properties;
    @Mock
    private Clock clock;
    @Mock
    private Workspace workspace;
    @Mock
    private DocumentGenerationJob job;
    @InjectMocks
    private DocumentGenerationClaimService claims;

    @Test
    @DisplayName("Workspace가 없으면 예외 없이 실행을 건너뛴다")
    void claim_success_skipMissingWorkspace() {
        // given
        when(workspaces.findIncludingDeletedByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.empty());

        // when & then
        assertThat(
                claims.claim(
                        WORKSPACE_ID,
                        JOB_ID
                )
        ).isEmpty();
        verifyNoInteractions(
                jobs,
                inputs,
                batches
        );
    }

    @Test
    @DisplayName("Workspace에 Job이 없으면 예외 없이 실행을 건너뛴다")
    void claim_success_skipMissingJob() {
        // given
        when(workspaces.findIncludingDeletedByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(
                jobs.findByWorkspaceIdAndIdForUpdate(
                        WORKSPACE_ID,
                        JOB_ID
                )
        ).thenReturn(Optional.empty());

        // when & then
        assertThat(
                claims.claim(
                        WORKSPACE_ID,
                        JOB_ID
                )
        ).isEmpty();
        verifyNoInteractions(
                inputs,
                batches
        );
    }

    @Test
    @DisplayName("실행 가능한 Job에 필수 Batch가 없으면 등록 상태 충돌로 전달한다")
    void claim_failure_missingBatch() {
        // given
        when(workspaces.findIncludingDeletedByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(
                jobs.findByWorkspaceIdAndIdForUpdate(
                        WORKSPACE_ID,
                        JOB_ID
                )
        ).thenReturn(Optional.of(job));
        when(clock.instant()).thenReturn(NOW);
        when(job.isReadyAt(NOW)).thenReturn(true);
        when(job.getTranscriptId()).thenReturn(TRANSCRIPT_ID);
        when(job.getBatchId()).thenReturn(BATCH_ID);
        when(
                inputs.findForUpdate(
                        WORKSPACE_ID,
                        TRANSCRIPT_ID
                )
        ).thenReturn(Optional.empty());
        when(batches.findByIdForUpdate(BATCH_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> claims.claim(
                        WORKSPACE_ID,
                        JOB_ID
                )
        ).isInstanceOf(DocumentException.class)
                .hasMessage(DocumentErrorCode.GENERATION_REGISTRATION_CONFLICT.getMessage());
    }
}
