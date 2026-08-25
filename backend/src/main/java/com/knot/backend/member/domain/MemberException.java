package com.knot.backend.member.domain;

import com.knot.backend.global.exception.ProjectException;

public class MemberException extends ProjectException {

    public MemberException(MemberErrorCode errorCode) {
        super(errorCode);
    }

    public MemberException(
            MemberErrorCode errorCode,
            Throwable cause
    ) {
        super(
                errorCode,
                cause
        );
    }
}
