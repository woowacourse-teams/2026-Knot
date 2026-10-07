package com.knot.backend.recording.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.recording.application.dto.result.PendingAudioUpload;
import com.knot.backend.recording.application.dto.result.RecordingAudioUploadCompletionResult;
import com.knot.backend.recording.application.dto.result.StoredAudioObject;
import com.knot.backend.recording.domain.RecordingAudioUploadStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RecordingAudioUploadCompletionServiceTest {
    private static final long WORKSPACE_ID = 10L;
    private static final long MEMBER_ID = 1L;
    private static final long RECORDING_ID = 7L;
    private static final long UPLOAD_ID = 30L;
    private static final String STORAGE_KEY = "recordings/7/key";

    private RecordingAudioUploadCompletionTransaction completionTransaction;
    private RecordingAudioStorage audioStorage;
    private RecordingAudioUploadCompletionService service;

    @BeforeEach
    void setUp() {
        completionTransaction = mock(RecordingAudioUploadCompletionTransaction.class);
        audioStorage = mock(RecordingAudioStorage.class);
        service = new RecordingAudioUploadCompletionService(
                completionTransaction,
                audioStorage
        );
    }

    @Test
    @DisplayName("완료 전 예약은 검증 트랜잭션 뒤 저장소를 확인하고 그 결과로 완료 트랜잭션을 실행한다")
    void complete_success_checksStorageBetweenTransactions() {
        // given
        StoredAudioObject storedObject = StoredAudioObject.of(
                1024L,
                "audio/webm"
        );
        when(prepare()).thenReturn(
                new PendingAudioUpload(
                        STORAGE_KEY,
                        false
                )
        );
        when(audioStorage.findStoredObject(STORAGE_KEY)).thenReturn(storedObject);
        when(complete(storedObject)).thenReturn(completedResult());

        // when
        RecordingAudioUploadCompletionResult result = service.complete(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID,
                UPLOAD_ID
        );

        // then
        assertThat(result.uploadStatus()).isEqualTo(RecordingAudioUploadStatus.COMPLETED);
        InOrder inOrder = inOrder(
                completionTransaction,
                audioStorage
        );
        inOrder.verify(completionTransaction)
                .prepare(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        UPLOAD_ID
                );
        inOrder.verify(audioStorage)
                .findStoredObject(STORAGE_KEY);
        inOrder.verify(completionTransaction)
                .complete(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        UPLOAD_ID,
                        storedObject
                );
    }

    @Test
    @DisplayName("이미 완료된 예약은 저장소를 다시 확인하지 않고 기존 결과를 반환한다")
    void complete_success_alreadyCompletedSkipsStorage() {
        // given
        when(prepare()).thenReturn(
                new PendingAudioUpload(
                        STORAGE_KEY,
                        true
                )
        );
        when(complete(StoredAudioObject.missing())).thenReturn(completedResult());

        // when
        RecordingAudioUploadCompletionResult result = service.complete(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID,
                UPLOAD_ID
        );

        // then
        assertThat(result.uploadId()).isEqualTo(UPLOAD_ID);
        verify(
                audioStorage,
                never()
        ).findStoredObject(anyString());
    }

    private PendingAudioUpload prepare() {
        return completionTransaction.prepare(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID,
                UPLOAD_ID
        );
    }

    private RecordingAudioUploadCompletionResult complete(StoredAudioObject storedObject) {
        return completionTransaction.complete(
                WORKSPACE_ID,
                MEMBER_ID,
                RECORDING_ID,
                UPLOAD_ID,
                storedObject
        );
    }

    private RecordingAudioUploadCompletionResult completedResult() {
        return new RecordingAudioUploadCompletionResult(
                RECORDING_ID,
                UPLOAD_ID,
                RecordingAudioUploadStatus.COMPLETED,
                Instant.parse("2026-10-05T01:00:00Z")
        );
    }
}
