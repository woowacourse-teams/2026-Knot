package com.knot.backend.recording.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum RecordingErrorCode implements ErrorCode {
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

    RECORDING_ALREADY_ENDED(
            ErrorCategory.CONFLICT,
            "RECORDING_ALREADY_ENDED",
            "이미 종료된 녹음입니다"
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
