package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum WorkspaceErrorCode implements ErrorCode {
    INVALID_WORKSPACE_NAME(
            ErrorCategory.INVALID_INPUT,
            "INVALID_WORKSPACE_NAME",
            "워크스페이스 이름이 올바르지 않습니다"
    ),

    INVALID_WORKSPACE_CREATED_AT(
            ErrorCategory.INVALID_INPUT,
            "INVALID_WORKSPACE_CREATED_AT",
            "워크스페이스 생성 시각이 올바르지 않습니다"
    ),

    INVALID_WORKSPACE_ID(
            ErrorCategory.INVALID_INPUT,
            "INVALID_WORKSPACE_ID",
            "워크스페이스 ID가 올바르지 않습니다"
    ),

    INVALID_MEMBER_ID(
            ErrorCategory.INVALID_INPUT,
            "INVALID_MEMBER_ID",
            "멤버 ID가 올바르지 않습니다"
    ),

    INVALID_WORKSPACE_MEMBER_ROLE(
            ErrorCategory.INVALID_INPUT,
            "INVALID_WORKSPACE_MEMBER_ROLE",
            "워크스페이스 멤버 역할이 올바르지 않습니다"
    ),

    INVALID_WORKSPACE_MEMBER_JOINED_AT(
            ErrorCategory.INVALID_INPUT,
            "INVALID_WORKSPACE_MEMBER_JOINED_AT",
            "워크스페이스 참여 시각이 올바르지 않습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    WorkspaceErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}
