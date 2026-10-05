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

    @Test
    @DisplayName("저장된 파일의 크기와 형식이 예약과 같으면 업로드를 완료한다")
    void complete_success_matchingFile() {
        // given
        RecordingAudioUpload upload = reserved();

        // when
        upload.complete(
                1024L,
                "audio/webm",
                RESERVED_AT.plusSeconds(60)
        );

        // then
        assertThat(upload.getStatus()).isEqualTo(RecordingAudioUploadStatus.COMPLETED);
        assertThat(upload.getCompletedAt()).isEqualTo(RESERVED_AT.plusSeconds(60));
    }

    @Test
    @DisplayName("저장된 파일의 크기가 예약과 다르면 업로드를 완료하지 않는다")
    void complete_failure_lengthMismatch() {
        // given
        RecordingAudioUpload upload = reserved();

        // when
        Throwable failure = catchThrowable(
                () -> upload.complete(
                        1000L,
                        "audio/webm",
                        RESERVED_AT.plusSeconds(60)
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.AUDIO_UPLOAD_NOT_COMPLETED);
        assertThat(upload.getStatus()).isEqualTo(RecordingAudioUploadStatus.RESERVED);
    }

    @Test
    @DisplayName("저장된 파일의 형식이 예약과 다르면 업로드를 완료하지 않는다")
    void complete_failure_typeMismatch() {
        // given
        RecordingAudioUpload upload = reserved();

        // when
        Throwable failure = catchThrowable(
                () -> upload.complete(
                        1024L,
                        "audio/mpeg",
                        RESERVED_AT.plusSeconds(60)
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.AUDIO_UPLOAD_NOT_COMPLETED);
    }

    @Test
    @DisplayName("이미 완료된 업로드는 다시 완료해도 처음 완료 시각을 유지한다")
    void complete_success_repeatedKeepsCompletedAt() {
        // given
        RecordingAudioUpload upload = reserved();
        upload.complete(
                1024L,
                "audio/webm",
                RESERVED_AT.plusSeconds(60)
        );

        // when
        upload.complete(
                1024L,
                "audio/webm",
                RESERVED_AT.plusSeconds(120)
        );

        // then
        assertThat(upload.getCompletedAt()).isEqualTo(RESERVED_AT.plusSeconds(60));
    }

    @Test
    @DisplayName("다른 녹음의 업로드 예약은 찾을 수 없는 것으로 거절한다")
    void validateBelongsTo_failure_otherRecording() {
        // given
        RecordingAudioUpload upload = reserved();

        // when
        Throwable failure = catchThrowable(() -> upload.validateBelongsTo(2L));

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.AUDIO_UPLOAD_NOT_FOUND);
    }

    private RecordingAudioUpload reserved() {
        return RecordingAudioUpload.reserve(
                1L,
                "recordings/1/key",
                "audio/webm",
                1024L,
                RESERVED_AT
        );
    }
}
