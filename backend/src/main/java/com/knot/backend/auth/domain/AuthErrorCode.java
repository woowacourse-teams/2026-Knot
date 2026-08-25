package com.knot.backend.auth.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum AuthErrorCode implements ErrorCode {
    UNAUTHENTICATED(ErrorCategory.UNAUTHORIZED, "UNAUTHENTICATED", "인증이 필요합니다"),

    OAUTH_AUTHENTICATION_FAILED(
            ErrorCategory.UNAUTHORIZED, "OAUTH_AUTHENTICATION_FAILED", "OAuth 인증에 실패했습니다"),

    INVALID_OAUTH_USER(ErrorCategory.UNAUTHORIZED, "INVALID_OAUTH_USER", "OAuth 사용자 정보가 올바르지 않습니다"),

    INVALID_AUTHENTICATED_MEMBER(
            ErrorCategory.UNAUTHORIZED, "INVALID_AUTHENTICATED_MEMBER", "인증 사용자 정보가 올바르지 않습니다"),

    INVALID_JWT(ErrorCategory.UNAUTHORIZED, "INVALID_JWT", "인증 토큰이 유효하지 않습니다"),

    JWT_CONFIGURATION_INVALID(
            ErrorCategory.INTERNAL_SERVER_ERROR, "JWT_CONFIGURATION_INVALID", "인증 설정이 올바르지 않습니다"),

    OAUTH_CONFIGURATION_INVALID(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "OAUTH_CONFIGURATION_INVALID",
            "OAuth 설정이 올바르지 않습니다");

    private final ErrorCategory category;
    private final String code;
    private final String message;

    AuthErrorCode(ErrorCategory category, String code, String message) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}
