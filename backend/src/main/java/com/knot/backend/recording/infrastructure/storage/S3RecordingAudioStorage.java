package com.knot.backend.recording.infrastructure.storage;

import com.knot.backend.recording.application.RecordingAudioStorage;
import com.knot.backend.recording.application.dto.result.PresignedAudioUpload;
import java.time.Duration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

public class S3RecordingAudioStorage implements RecordingAudioStorage {
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration uploadUrlTtl;

    public S3RecordingAudioStorage(
            S3Presigner presigner,
            String bucket,
            Duration uploadUrlTtl
    ) {
        this.presigner = presigner;
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
}
