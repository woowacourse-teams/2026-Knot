package com.knot.backend.chat.domain;

import com.knot.backend.global.exception.ErrorCategory;
import com.knot.backend.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public enum ChatErrorCode implements ErrorCode {
    INVALID_CHAT_SESSION_WORKSPACE_ID(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_SESSION_WORKSPACE_ID",
            "채팅 세션의 워크스페이스 ID가 올바르지 않습니다"
    ),

    INVALID_CHAT_SESSION_MEMBER_ID(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_SESSION_MEMBER_ID",
            "채팅 세션의 멤버 ID가 올바르지 않습니다"
    ),

    INVALID_CHAT_SESSION_TITLE(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_SESSION_TITLE",
            "채팅 세션 제목이 올바르지 않습니다"
    ),

    INVALID_CHAT_SESSION_CREATED_AT(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_SESSION_CREATED_AT",
            "채팅 세션 생성 시각이 올바르지 않습니다"
    ),

    INVALID_CHAT_SESSION_LAST_MESSAGE_AT(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_SESSION_LAST_MESSAGE_AT",
            "채팅 세션의 마지막 메시지 시각이 올바르지 않습니다"
    ),

    INVALID_CHAT_MESSAGE_SESSION_ID(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_MESSAGE_SESSION_ID",
            "채팅 메시지의 세션 ID가 올바르지 않습니다"
    ),

    INVALID_CHAT_MESSAGE_ROLE(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_MESSAGE_ROLE",
            "채팅 메시지 역할이 올바르지 않습니다"
    ),

    INVALID_CHAT_MESSAGE_CONTENT(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_MESSAGE_CONTENT",
            "채팅 메시지 내용이 올바르지 않습니다"
    ),

    INVALID_CHAT_MESSAGE_CREATED_AT(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_MESSAGE_CREATED_AT",
            "채팅 메시지 생성 시각이 올바르지 않습니다"
    ),

    INVALID_CHAT_FEEDBACK_MESSAGE_ID(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_FEEDBACK_MESSAGE_ID",
            "채팅 피드백의 메시지 ID가 올바르지 않습니다"
    ),

    INVALID_CHAT_FEEDBACK_MEMBER_ID(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_FEEDBACK_MEMBER_ID",
            "채팅 피드백의 멤버 ID가 올바르지 않습니다"
    ),

    INVALID_CHAT_FEEDBACK_RESULT(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_FEEDBACK_RESULT",
            "채팅 피드백 결과가 올바르지 않습니다"
    ),

    INVALID_CHAT_FEEDBACK_CREATED_AT(
            ErrorCategory.INVALID_INPUT,
            "INVALID_CHAT_FEEDBACK_CREATED_AT",
            "채팅 피드백 생성 시각이 올바르지 않습니다"
    ),

    CHAT_SESSION_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "CHAT_SESSION_NOT_FOUND",
            "채팅 세션을 찾을 수 없습니다"
    ),

    CHAT_MESSAGE_NOT_FOUND(
            ErrorCategory.NOT_FOUND,
            "CHAT_MESSAGE_NOT_FOUND",
            "채팅 메시지를 찾을 수 없습니다"
    ),

    CHAT_ACCESS_DENIED(
            ErrorCategory.FORBIDDEN,
            "CHAT_ACCESS_DENIED",
            "채팅에 접근할 권한이 없습니다"
    ),

    CHAT_STREAM_ALREADY_ACTIVE(
            ErrorCategory.CONFLICT,
            "CHAT_STREAM_ALREADY_ACTIVE",
            "채팅 응답이 이미 진행 중입니다"
    ),

    LLM_CONFIGURATION_INVALID(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_CONFIGURATION_INVALID",
            "LLM 설정이 올바르지 않습니다"
    ),

    LLM_STREAM_FAILED(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_STREAM_FAILED",
            "답변 생성에 실패했습니다"
    ),

    LLM_STREAM_TIMEOUT(
            ErrorCategory.INTERNAL_SERVER_ERROR,
            "LLM_STREAM_TIMEOUT",
            "답변 생성 시간이 초과되었습니다"
    );

    private final ErrorCategory category;
    private final String code;
    private final String message;

    ChatErrorCode(
            ErrorCategory category,
            String code,
            String message
    ) {
        this.category = category;
        this.code = code;
        this.message = message;
    }
}
