package com.knot.backend.recording.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource({"audio/mpeg,1024", "audio/webm,0", "audio/webm,524288001"})
    @DisplayName("허용되지 않은 형식, 0 이하 크기, 500MB 초과 파일은 예약하지 않는다")
    void reserve_failure_disallowedFile(
            String contentType,
            long contentLength
    ) {
        // given
        String storageKey = "recordings/1/key";

        // when
        Throwable failure = catchThrowable(
                () -> RecordingAudioUpload.reserve(
                        1L,
                        storageKey,
                        contentType,
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
    @DisplayName("완료되지 않은 예약은 같은 형식·크기로 재발급할 수 있다")
    void validateReissuable_success_sameFile() {
        // given
        RecordingAudioUpload upload = reservedUpload();

        // when
        Throwable failure = catchThrowable(
                () -> upload.validateReissuable(
                        "audio/webm",
                        1024L
                )
        );

        // then
        assertThat(failure).isNull();
    }

    @Test
    @DisplayName("재발급 때 파일 크기가 다르면 이전 URL과 다른 파일이 같은 key에 올라갈 수 있어 거절한다")
    void validateReissuable_failure_differentFile() {
        // given
        RecordingAudioUpload upload = reservedUpload();

        // when
        Throwable failure = catchThrowable(
                () -> upload.validateReissuable(
                        "audio/webm",
                        2048L
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.INVALID_AUDIO_UPLOAD);
    }

    private RecordingAudioUpload reservedUpload() {
        return RecordingAudioUpload.reserve(
                1L,
                "recordings/1/key",
                "audio/webm",
                1024L,
                RESERVED_AT
        );
    }
}
