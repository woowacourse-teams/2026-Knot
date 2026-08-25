package com.knot.backend.workspace.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;

public enum WorkspaceErrorCode implements ErrorCode {
    INVALID_WORKSPACE_NAME,
    INVALID_WORKSPACE_CREATED_AT,
    INVALID_WORKSPACE_ID,
    INVALID_MEMBER_ID,
    INVALID_WORKSPACE_MEMBER_ROLE,
    INVALID_WORKSPACE_MEMBER_JOINED_AT;

    @Override
    public ErrorCategory getCategory() {
        return ErrorCategory.INVALID_INPUT;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return switch (this) {
            case INVALID_WORKSPACE_NAME -> "워크스페이스 이름이 올바르지 않습니다";
            case INVALID_WORKSPACE_CREATED_AT -> "워크스페이스 생성 시각이 올바르지 않습니다";
            case INVALID_WORKSPACE_ID -> "워크스페이스 ID가 올바르지 않습니다";
            case INVALID_MEMBER_ID -> "멤버 ID가 올바르지 않습니다";
            case INVALID_WORKSPACE_MEMBER_ROLE -> "워크스페이스 멤버 역할이 올바르지 않습니다";
            case INVALID_WORKSPACE_MEMBER_JOINED_AT -> "워크스페이스 참여 시각이 올바르지 않습니다";
        };
    }
}
