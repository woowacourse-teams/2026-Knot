package com.knot.backend.recording.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingAudioUploadTest {
    private static final Instant RESERVED_AT = Instant.parse("2026-10-05T00:00:00Z");

    @Test
    @DisplayName("업로드를 예약하면 RESERVED 상태로 파일 정보와 서버가 정한 저장소 key를 저장한다")
    void reserve_success() {
        // given
        String storageKey = "recordings/1/key";

        // when
        RecordingAudioUpload upload = RecordingAudioUpload.reserve(
                1L,
                storageKey,
                "audio/webm",
                1024L,
                RESERVED_AT
        );

        // then
        assertThat(upload.getStatus()).isEqualTo(RecordingAudioUploadStatus.RESERVED);
        assertThat(upload.getStorageKey()).isEqualTo(storageKey);
        assertThat(upload.getContentType()).isEqualTo("audio/webm");
        assertThat(upload.getContentLength()).isEqualTo(1024L);
        assertThat(upload.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("크기가 0 이하인 파일은 예약하지 않는다")
    void reserve_failure_nonPositiveLength() {
        // given
        long contentLength = 0L;

        // when
        Throwable failure = catchThrowable(
                () -> RecordingAudioUpload.reserve(
                        1L,
                        "recordings/1/key",
                        "audio/webm",
                        contentLength,
                        RESERVED_AT
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.INVALID_AUDIO_UPLOAD);
    }

    @Test
    @DisplayName("저장소 key가 없으면 예약하지 않는다")
    void reserve_failure_blankStorageKey() {
        // given
        String storageKey = " ";

        // when
        Throwable failure = catchThrowable(
                () -> RecordingAudioUpload.reserve(
                        1L,
                        storageKey,
                        "audio/webm",
                        1024L,
                        RESERVED_AT
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.INVALID_RECORDING_DATA);
    }

    @Test
    @DisplayName("완료되지 않은 예약은 파일 형식과 크기를 바꾸고 저장소 key는 유지한다")
    void changeFile_success_reserved() {
        // given
        RecordingAudioUpload upload = RecordingAudioUpload.reserve(
                1L,
                "recordings/1/key",
                "audio/webm",
                1024L,
                RESERVED_AT
        );

        // when
        upload.changeFile(
                "audio/webm",
                2048L
        );

        // then
        assertThat(upload.getContentLength()).isEqualTo(2048L);
        assertThat(upload.getStorageKey()).isEqualTo("recordings/1/key");
        assertThat(upload.getStatus()).isEqualTo(RecordingAudioUploadStatus.RESERVED);
    }
}
