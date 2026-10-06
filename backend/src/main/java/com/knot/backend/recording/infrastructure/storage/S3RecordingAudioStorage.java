package com.knot.backend.recording.infrastructure.storage;

import com.knot.backend.recording.application.RecordingAudioStorage;
import com.knot.backend.recording.application.dto.result.PresignedAudioUpload;
import com.knot.backend.recording.application.dto.result.StoredAudioObject;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import java.time.Duration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

public class S3RecordingAudioStorage implements RecordingAudioStorage {
    private static final int NOT_FOUND_STATUS = 404;

    private final S3Presigner presigner;
    private final S3Client client;
    private final String bucket;
    private final Duration uploadUrlTtl;

    public S3RecordingAudioStorage(
            S3Presigner presigner,
            S3Client client,
            String bucket,
            Duration uploadUrlTtl
    ) {
        this.presigner = presigner;
        this.client = client;
        this.bucket = bucket;
        this.uploadUrlTtl = uploadUrlTtl;
    }

    // Content-Type과 Content-Length를 서명에 넣어 예약한 파일과 다른 형식·크기의 PUT을 저장소가 거절하게 한다.
    @Override
    public PresignedAudioUpload presignUpload(
            String storageKey,
            String contentType,
            long contentLength
    ) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(uploadUrlTtl)
                        .putObjectRequest(putObjectRequest)
                        .build()
        );
        return new PresignedAudioUpload(
                presigned.url()
                        .toString(),
                presigned.expiration()
        );
    }

    @Override
    public StoredAudioObject findStoredObject(String storageKey) {
        try {
            HeadObjectResponse response = client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageKey)
                            .build()
            );
            return StoredAudioObject.of(
                    response.contentLength(),
                    response.contentType()
            );
        } catch (S3Exception exception) {
            if (exception.statusCode() == NOT_FOUND_STATUS) {
                return StoredAudioObject.missing();
            }
            throw new RecordingException(RecordingErrorCode.AUDIO_STORAGE_UNAVAILABLE);
        } catch (SdkException exception) {
            throw new RecordingException(RecordingErrorCode.AUDIO_STORAGE_UNAVAILABLE);
        }
    }
}
