package com.knot.backend.recording.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum RecordingErrorCode implements ErrorCode {

    AUDIO_DELETION_CONFLICT(
            ErrorCategory.CONFLICT,
            "AUDIO_DELETION_CONFLICT",
            "오디오 삭제 작업을 실행할 수 없습니다"
    ),

    INVALID_RECORDING_DATA(
            ErrorCategory.INVALID_INPUT,
            "INVALID_RECORDING_DATA",
            "녹음 요청 값이 올바르지 않습니다"
    ),

    INVALID_RECORDING_TIME(
            ErrorCategory.INVALID_INPUT,
            "INVALID_RECORDING_TIME",
            "녹음 시각이 올바르지 않습니다"
    ),

    RECORDING_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "RECORDING_NOT_FOUND",
            "녹음을 찾을 수 없습니다"
    ),

    RECORDING_CONTROL_DENIED(
            ErrorCategory.FORBIDDEN,
            "RECORDING_CONTROL_DENIED",
            "녹음을 제어할 권한이 없습니다"
    ),

    RECORDING_ALREADY_ENDED(
            ErrorCategory.CONFLICT,
            "RECORDING_ALREADY_ENDED",
            "이미 종료된 녹음입니다"
    ),

    RECORDING_ALREADY_DISCARDED(
            ErrorCategory.CONFLICT,
            "RECORDING_ALREADY_DISCARDED",
            "이미 폐기된 녹음입니다"
    ),

    RECORDING_NOT_ENDED(
            ErrorCategory.CONFLICT,
            "RECORDING_NOT_ENDED",
            "종료된 녹음만 오디오를 업로드할 수 있습니다"
    ),

    INVALID_AUDIO_UPLOAD(
            ErrorCategory.INVALID_INPUT,
            "INVALID_AUDIO_UPLOAD",
            "업로드할 오디오의 형식이나 크기가 올바르지 않습니다"
    ),

    AUDIO_UPLOAD_ALREADY_COMPLETED(
            ErrorCategory.CONFLICT,
            "AUDIO_UPLOAD_ALREADY_COMPLETED",
            "이미 오디오 업로드가 완료된 녹음입니다"
    ),

    AUDIO_UPLOAD_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "AUDIO_UPLOAD_NOT_FOUND",
            "오디오 업로드 예약을 찾을 수 없습니다"
    ),

    AUDIO_UPLOAD_NOT_COMPLETED(
            ErrorCategory.CONFLICT,
            "AUDIO_UPLOAD_NOT_COMPLETED",
            "예약한 오디오 파일이 저장소에 올라가지 않았습니다"
    ),

    AUDIO_STORAGE_UNAVAILABLE(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "AUDIO_STORAGE_UNAVAILABLE",
            "오디오 저장소를 사용할 수 없습니다"
    ),

    ACTIVE_RECORDING_ALREADY_EXISTS(
            ErrorCategory.CONFLICT,
            "ACTIVE_RECORDING_ALREADY_EXISTS",
            "이미 진행 중인 녹음이 있습니다"
    ),

    RECORDING_START_REQUEST_CONFLICT(
            ErrorCategory.CONFLICT,
            "RECORDING_START_REQUEST_CONFLICT",
            "녹음 시작 요청이 기존 요청과 다릅니다"
    ),

    RECORDING_MEMBER_NOT_FOUND(
            ErrorCategory.UNAUTHORIZED,
            "RECORDING_MEMBER_NOT_FOUND",
            "인증된 멤버를 찾을 수 없습니다"
    ),

    RECORDING_CONTROL_HASH_FAILED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "RECORDING_CONTROL_HASH_FAILED",
            "녹음 제어 증명을 처리할 수 없습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    RecordingErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}
