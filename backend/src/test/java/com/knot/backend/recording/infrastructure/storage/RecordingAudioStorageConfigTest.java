package com.knot.backend.recording.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.recording.application.RecordingAudioStorage;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingAudioStorageConfigTest {

    @Test
    @DisplayName("저장소 설정이 비어 있으면 서버는 뜨고 업로드 URL 요청만 저장소 사용 불가로 거절한다")
    void recordingAudioStorage_success_unconfiguredRejectsUpload() {
        // given
        RecordingAudioStorage storage = new RecordingAudioStorageConfig().recordingAudioStorage(properties(""));

        // when
        Throwable failure = catchThrowable(
                () -> storage.presignUpload(
                        "recordings/1/key",
                        "audio/webm",
                        10L
                )
        );

        // then
        assertThat(storage).isInstanceOf(UnconfiguredRecordingAudioStorage.class);
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.AUDIO_STORAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("저장소 설정이 모두 있으면 S3 호환 저장소를 사용한다")
    void recordingAudioStorage_success_configured() {
        // given
        RecordingAudioStorageProperties properties = properties("knot-audio");

        // when
        RecordingAudioStorage storage = new RecordingAudioStorageConfig().recordingAudioStorage(properties);

        // then
        assertThat(storage).isInstanceOf(S3RecordingAudioStorage.class);
    }

    @Test
    @DisplayName("설정 문자열에는 접근 키와 비밀 키를 남기지 않는다")
    void toString_success_redactsKeys() {
        // given
        RecordingAudioStorageProperties properties = properties("knot-audio");

        // when
        String text = properties.toString();

        // then
        assertThat(text).doesNotContain("test-access-key")
                .doesNotContain("test-secret-key");
    }

    @Test
    @DisplayName("고정 키가 둘 다 없으면 자격 증명을 조회하지 않고 기본 공급자를 구성한다")
    void recordingAudioStorage_success_defaultCredentials() {
        // given
        RecordingAudioStorageProperties properties = propertiesWithCredentials(
                "",
                ""
        );

        // when
        RecordingAudioStorage storage = new RecordingAudioStorageConfig().recordingAudioStorage(properties);

        // then
        assertThat(storage).isInstanceOf(S3RecordingAudioStorage.class);
    }

    @Test
    @DisplayName("접근 키만 있으면 다른 인증 방식으로 우회하지 않고 저장소 사용 불가로 거절한다")
    void recordingAudioStorage_failure_onlyAccessKey() {
        // given
        RecordingAudioStorageProperties properties = propertiesWithCredentials(
                "test-access-key",
                ""
        );

        // when
        RecordingAudioStorage storage = new RecordingAudioStorageConfig().recordingAudioStorage(properties);

        // then
        assertThat(storage).isInstanceOf(UnconfiguredRecordingAudioStorage.class);
    }

    @Test
    @DisplayName("비밀 키만 있으면 다른 인증 방식으로 우회하지 않고 저장소 사용 불가로 거절한다")
    void recordingAudioStorage_failure_onlySecretKey() {
        // given
        RecordingAudioStorageProperties properties = propertiesWithCredentials(
                "",
                "test-secret-key"
        );

        // when
        RecordingAudioStorage storage = new RecordingAudioStorageConfig().recordingAudioStorage(properties);

        // then
        assertThat(storage).isInstanceOf(UnconfiguredRecordingAudioStorage.class);
    }

    private RecordingAudioStorageProperties propertiesWithCredentials(
            String accessKey,
            String secretKey
    ) {
        return new RecordingAudioStorageProperties(
                "https://s3.ap-northeast-2.amazonaws.com",
                "ap-northeast-2",
                "knot-audio",
                accessKey,
                secretKey,
                "knot/dev/",
                Duration.ofMinutes(15)
        );
    }

    private RecordingAudioStorageProperties properties(String bucket) {
        return new RecordingAudioStorageProperties(
                "https://kr.object.ncloudstorage.com",
                "kr-standard",
                bucket,
                "test-access-key",
                "test-secret-key",
                "",
                Duration.ofMinutes(15)
        );
    }
}
