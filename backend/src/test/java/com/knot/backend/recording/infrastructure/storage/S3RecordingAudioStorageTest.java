package com.knot.backend.recording.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.recording.application.dto.result.PresignedAudioUpload;
import com.knot.backend.recording.application.dto.result.StoredAudioObject;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3RecordingAudioStorageTest {
    private static final String BUCKET = "knot-audio";

    private HttpServer server;
    private final AtomicReference<String> lastRequest = new AtomicReference<>();
    private final AtomicReference<Integer> headStatus = new AtomicReference<>(200);
    private S3RecordingAudioStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(
                        "localhost",
                        0
                ),
                0
        );
        server.createContext(
                "/",
                exchange -> {
                    lastRequest.set(
                            exchange.getRequestMethod() + " " + exchange.getRequestURI()
                                    .getPath()
                    );
                    exchange.getResponseHeaders()
                            .add(
                                    "Content-Type",
                                    "audio/webm"
                            );
                    exchange.getResponseHeaders()
                            .add(
                                    "Content-Length",
                                    "1024"
                            );
                    exchange.sendResponseHeaders(
                            headStatus.get(),
                            -1
                    );
                    exchange.close();
                }
        );
        server.start();
        URI endpoint = URI.create(
                "http://localhost:" + server.getAddress()
                        .getPort()
        );
        storage = new S3RecordingAudioStorage(
                presigner(endpoint),
                client(endpoint),
                BUCKET,
                "",
                Duration.ofMinutes(15)
        );
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("서버가 정한 key로 버킷에 PUT할 URL을 서명하고 형식·크기를 서명 조건에 포함한다")
    void presignUpload_success_signsContentTypeAndLength() {
        // given
        Instant before = Instant.now();

        // when
        PresignedAudioUpload presigned = storage.presignUpload(
                "recordings/7/key",
                "audio/webm",
                1024L
        );

        // then
        assertThat(presigned.uploadUrl()).contains("/knot-audio/recordings/7/key?")
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

    @Test
    @DisplayName("저장소에 객체가 있으면 HEAD로 크기와 형식을 읽는다")
    void findStoredObject_success_existingObject() {
        // given
        headStatus.set(200);

        // when
        StoredAudioObject storedObject = storage.findStoredObject("recordings/7/key");

        // then
        assertThat(lastRequest.get()).isEqualTo("HEAD /knot-audio/recordings/7/key");
        assertThat(storedObject).isEqualTo(
                StoredAudioObject.of(
                        1024L,
                        "audio/webm"
                )
        );
    }

    @Test
    @DisplayName("저장소에 객체가 없으면 업로드되지 않은 것으로 본다")
    void findStoredObject_success_missingObject() {
        // given
        headStatus.set(404);

        // when
        StoredAudioObject storedObject = storage.findStoredObject("recordings/7/key");

        // then
        assertThat(storedObject.exists()).isFalse();
    }

    @Test
    @DisplayName("저장소가 오류를 반환하면 재시도 가능한 저장소 사용 불가로 알린다")
    void findStoredObject_failure_storageError() {
        // given
        headStatus.set(500);

        // when
        Throwable failure = catchThrowable(() -> storage.findStoredObject("recordings/7/key"));

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.AUDIO_STORAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("설정 접두사를 논리 key 앞에 붙여 업로드 URL을 발급한다")
    void presignUpload_success_configuredPrefix() {
        // given
        S3RecordingAudioStorage prefixedStorage = storageWithPrefix("knot/dev/");

        // when
        PresignedAudioUpload presigned = prefixedStorage.presignUpload(
                "recordings/7/key",
                "audio/webm",
                1024L
        );

        // then
        assertThat(presigned.uploadUrl()).contains("/knot-audio/knot/dev/recordings/7/key?");
    }

    @Test
    @DisplayName("HEAD도 업로드와 같은 설정 접두사를 사용한다")
    void findStoredObject_success_configuredPrefix() {
        // given
        S3RecordingAudioStorage prefixedStorage = storageWithPrefix("knot/dev/");

        // when
        StoredAudioObject storedObject = prefixedStorage.findStoredObject("recordings/7/key");

        // then
        assertThat(lastRequest.get()).isEqualTo("HEAD /knot-audio/knot/dev/recordings/7/key");
        assertThat(storedObject.exists()).isTrue();
    }

    private S3RecordingAudioStorage storageWithPrefix(String keyPrefix) {
        URI endpoint = URI.create(
                "http://localhost:" + server.getAddress()
                        .getPort()
        );
        return new S3RecordingAudioStorage(
                presigner(endpoint),
                client(endpoint),
                BUCKET,
                keyPrefix,
                Duration.ofMinutes(15)
        );
    }

    private S3Presigner presigner(URI endpoint) {
        return S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.of("kr-standard"))
                .credentialsProvider(credentials())
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();
    }

    private S3Client client(URI endpoint) {
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of("kr-standard"))
                .credentialsProvider(credentials())
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .retryStrategy(RetryMode.STANDARD)
                                .apiCallTimeout(Duration.ofSeconds(5))
                                .build()
                )
                .build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        "test-access-key",
                        "test-secret-key"
                )
        );
    }
}
