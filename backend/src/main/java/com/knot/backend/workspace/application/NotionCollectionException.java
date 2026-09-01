package com.knot.backend.workspace.application;

import java.io.Serial;
import java.util.Objects;

public final class NotionCollectionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final String ACCESS_DENIED_MESSAGE = "Notion 접근 권한을 확인할 수 없습니다";
    private static final String NOT_FOUND_MESSAGE = "Notion 공유 문서를 찾을 수 없습니다";
    private static final String INVALID_REQUEST_MESSAGE = "Notion 문서 수집 요청이 올바르지 않습니다";
    private static final String RATE_LIMITED_MESSAGE = "Notion 요청 제한으로 문서를 수집할 수 없습니다";
    private static final String TEMPORARY_MESSAGE = "Notion 일시 장애로 문서를 수집할 수 없습니다";
    private static final String INVALID_RESPONSE_MESSAGE = "Notion 응답을 문서로 변환할 수 없습니다";

    private final NotionCollectionFailureType failureType;

    public NotionCollectionException(NotionCollectionFailureType failureType) {
        super(messageOf(failureType));
        this.failureType = Objects.requireNonNull(
                failureType,
                "failureType"
        );
    }

    public NotionCollectionFailureType getFailureType() {
        return failureType;
    }

    private static String messageOf(NotionCollectionFailureType failureType) {
        return switch (Objects.requireNonNull(
                failureType,
                "failureType"
        )) {
            case ACCESS_DENIED -> ACCESS_DENIED_MESSAGE;
            case NOT_FOUND -> NOT_FOUND_MESSAGE;
            case INVALID_REQUEST -> INVALID_REQUEST_MESSAGE;
            case RATE_LIMITED -> RATE_LIMITED_MESSAGE;
            case TEMPORARY -> TEMPORARY_MESSAGE;
            case INVALID_RESPONSE -> INVALID_RESPONSE_MESSAGE;
        };
    }
}
