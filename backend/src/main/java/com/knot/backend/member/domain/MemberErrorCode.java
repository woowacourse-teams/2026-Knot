package com.knot.backend.member.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum MemberErrorCode implements ErrorCode {
    INVALID_MEMBER_DATA(ErrorCategory.INVALID_INPUT, "INVALID_MEMBER_DATA", "회원 정보가 올바르지 않습니다"),

    GITHUB_ID_CANNOT_BE_CHANGED(
            ErrorCategory.CONFLICT, "GITHUB_ID_CANNOT_BE_CHANGED", "회원의 GitHub ID는 변경할 수 없습니다"),

    MEMBER_LOGIN_FAILED(
            ErrorCategory.INTERNAL_SERVER_ERROR, "MEMBER_LOGIN_FAILED", "회원 로그인 처리에 실패했습니다");

    private final ErrorCategory category;
    private final String code;
    private final String message;

    MemberErrorCode(ErrorCategory category, String code, String message) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}
