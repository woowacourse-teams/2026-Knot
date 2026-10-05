package com.knot.backend.recording.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.recording.application.dto.result.PresignedAudioUpload;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3RecordingAudioStorageTest {

    @Test
    @DisplayName("서버가 정한 key로 버킷에 PUT할 URL을 서명하고 형식·크기를 서명 조건에 포함한다")
    void presignUpload_success_signsContentTypeAndLength() {
        // given
        S3RecordingAudioStorage storage = new S3RecordingAudioStorage(
                presigner(),
                "knot-audio",
                Duration.ofMinutes(15)
        );
        Instant before = Instant.now();

        // when
        PresignedAudioUpload presigned = storage.presignUpload(
                "recordings/7/key",
                "audio/webm",
                1024L
        );

        // then
        assertThat(presigned.uploadUrl()).startsWith("https://kr.object.ncloudstorage.com/knot-audio/recordings/7/key?")
                .contains("X-Amz-Expires=900")
                .contains("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost")
                .doesNotContain("test-secret-key");
        assertThat(presigned.expiresAt()).isBetween(
                before.plus(Duration.ofMinutes(15))
                        .minusSeconds(5),
                Instant.now()
                        .plus(Duration.ofMinutes(15))
        );
    }

    private S3Presigner presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create("https://kr.object.ncloudstorage.com"))
                .region(Region.of("kr-standard"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        "test-access-key",
                                        "test-secret-key"
                                )
                        )
                )
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();
    }
}
