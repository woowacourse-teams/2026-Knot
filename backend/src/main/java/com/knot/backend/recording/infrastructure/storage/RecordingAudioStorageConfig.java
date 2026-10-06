package com.knot.backend.recording.infrastructure.storage;

import com.knot.backend.recording.application.RecordingAudioStorage;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecordingAudioStorageProperties.class)
public class RecordingAudioStorageConfig {

    @Bean
    public RecordingAudioStorage recordingAudioStorage(RecordingAudioStorageProperties properties) {
        if (!properties.isConfigured()) {
            return new UnconfiguredRecordingAudioStorage();
        }
        return new S3RecordingAudioStorage(
                presigner(properties),
                properties.bucket(),
                properties.uploadUrlTtl()
        );
    }

    // NCP Object Storage 등 S3 호환 저장소를 같은 코드로 쓰기 위해 endpoint를 지정하고 path-style 주소를
    // 사용한다.
    private S3Presigner presigner(RecordingAudioStorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        properties.accessKey(),
                                        properties.secretKey()
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
