package com.knot.backend.recording.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.recording.domain.RecordingException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3RecordingAudioDeletionTest {

    private S3Client client;
    private S3RecordingAudioStorage storage;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        storage = new S3RecordingAudioStorage(
                mock(S3Presigner.class),
                client,
                "bucket",
                "prefix/",
                Duration.ofMinutes(15)
        );
        when(client.getBucketVersioning(any(GetBucketVersioningRequest.class))).thenReturn(
                GetBucketVersioningResponse.builder()
                        .build()
        );
    }

    @Test
    @DisplayName("버전 없는 객체는 업로드와 같은 key로 삭제한다")
    void deleteStoredObject_unversionedDeletesExactKey() {
        storage.deleteStoredObject("key");

        verify(client).deleteObject(
                DeleteObjectRequest.builder()
                        .bucket("bucket")
                        .key("prefix/key")
                        .build()
        );
    }

    @Test
    @DisplayName("이미 없는 파일의 반복 삭제는 성공으로 처리한다")
    void deleteStoredObject_missingFileIsIdempotent() {
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(
                S3Exception.builder()
                        .statusCode(404)
                        .build()
        );

        storage.deleteStoredObject("key");
    }

    @Test
    @DisplayName("버전 활성 또는 중단 버킷에서는 대상 key의 모든 버전과 마커를 삭제한다")
    void deleteStoredObject_versionedRemovesOnlyExactKey() {
        when(client.getBucketVersioning(any(GetBucketVersioningRequest.class))).thenReturn(
                GetBucketVersioningResponse.builder()
                        .status(BucketVersioningStatus.SUSPENDED)
                        .build()
        );
        when(client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(
                                List.of(
                                        ObjectVersion.builder()
                                                .key("prefix/key")
                                                .versionId("v1")
                                                .build(),
                                        ObjectVersion.builder()
                                                .key("prefix/key-other")
                                                .versionId("v2")
                                                .build()
                                )
                        )
                        .deleteMarkers(
                                DeleteMarkerEntry.builder()
                                        .key("prefix/key")
                                        .versionId("m1")
                                        .build()
                        )
                        .isTruncated(false)
                        .build()
        );

        storage.deleteStoredObject("key");

        verify(client).deleteObject(
                DeleteObjectRequest.builder()
                        .bucket("bucket")
                        .key("prefix/key")
                        .versionId("v1")
                        .build()
        );
        verify(client).deleteObject(
                DeleteObjectRequest.builder()
                        .bucket("bucket")
                        .key("prefix/key")
                        .versionId("m1")
                        .build()
        );
        verify(
                client,
                never()
        ).deleteObject(
                DeleteObjectRequest.builder()
                        .bucket("bucket")
                        .key("prefix/key-other")
                        .versionId("v2")
                        .build()
        );
    }

    @Test
    @DisplayName("버전 목록의 다음 페이지도 처리한다")
    void deleteStoredObject_paginationDeletesAllVersions() {
        when(client.getBucketVersioning(any(GetBucketVersioningRequest.class))).thenReturn(
                GetBucketVersioningResponse.builder()
                        .status(BucketVersioningStatus.ENABLED)
                        .build()
        );
        when(client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(
                                ObjectVersion.builder()
                                        .key("prefix/key")
                                        .versionId("v1")
                                        .build()
                        )
                        .isTruncated(true)
                        .nextKeyMarker("prefix/key")
                        .nextVersionIdMarker("v1")
                        .build(),
                ListObjectVersionsResponse.builder()
                        .versions(
                                ObjectVersion.builder()
                                        .key("prefix/key")
                                        .versionId("v2")
                                        .build()
                        )
                        .isTruncated(false)
                        .build()
        );

        storage.deleteStoredObject("key");

        verify(client).listObjectVersions(
                ListObjectVersionsRequest.builder()
                        .bucket("bucket")
                        .prefix("prefix/key")
                        .keyMarker("prefix/key")
                        .versionIdMarker("v1")
                        .build()
        );
        verify(client).deleteObject(
                DeleteObjectRequest.builder()
                        .bucket("bucket")
                        .key("prefix/key")
                        .versionId("v2")
                        .build()
        );
    }

    @Test
    @DisplayName("권한 및 버전 조회 오류는 삭제 성공으로 기록하지 않는다")
    void deleteStoredObject_storageFailureThrowsTypedError() {
        when(client.getBucketVersioning(any(GetBucketVersioningRequest.class))).thenThrow(
                S3Exception.builder()
                        .statusCode(403)
                        .build()
        );

        assertThatThrownBy(() -> storage.deleteStoredObject("key")).isInstanceOf(RecordingException.class);
        verify(
                client,
                never()
        ).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("버전 ID를 알 수 없으면 일반 삭제로 대체하지 않고 재처리한다")
    void deleteStoredObject_failureMissingVersionId() {
        when(client.getBucketVersioning(any(GetBucketVersioningRequest.class))).thenReturn(
                GetBucketVersioningResponse.builder()
                        .status(BucketVersioningStatus.ENABLED)
                        .build()
        );
        when(client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(
                                ObjectVersion.builder()
                                        .key("prefix/key")
                                        .build()
                        )
                        .isTruncated(false)
                        .build()
        );

        assertThatThrownBy(() -> storage.deleteStoredObject("key")).isInstanceOf(RecordingException.class);
        verify(
                client,
                never()
        ).deleteObject(any(DeleteObjectRequest.class));
    }
}
