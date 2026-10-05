package com.knot.backend.recording.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingAudioUploadPolicyTest {
    private final RecordingAudioUploadPolicy policy = new RecordingAudioUploadPolicy(
            Set.of("audio/webm"),
            1000L
    );

    @Test
    @DisplayName("허용된 형식이고 최대 크기 이하인 파일은 통과한다")
    void validate_success_allowedFile() {
        // given
        long contentLength = 1000L;

        // when
        Throwable failure = catchThrowable(
                () -> policy.validate(
                        "audio/webm",
                        contentLength
                )
        );

        // then
        assertThat(failure).isNull();
    }

    @ParameterizedTest
    @CsvSource({"audio/mpeg,10", "audio/webm,1001", "audio/webm,0"})
    @DisplayName("허용되지 않은 형식, 최대 크기 초과, 0 이하 크기는 거절한다")
    void validate_failure_disallowedFile(
            String contentType,
            long contentLength
    ) {
        // given
        RecordingAudioUploadPolicy uploadPolicy = policy;

        // when
        Throwable failure = catchThrowable(
                () -> uploadPolicy.validate(
                        contentType,
                        contentLength
                )
        );

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.INVALID_AUDIO_UPLOAD);
    }
}
