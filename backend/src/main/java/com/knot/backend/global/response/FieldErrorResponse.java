package com.knot.backend.global.response;

import java.util.Objects;

public record FieldErrorResponse(
        String field,
        String reason
) {

    public FieldErrorResponse {
        Objects.requireNonNull(
                field,
                "field"
        );
        Objects.requireNonNull(
                reason,
                "reason"
        );
    }
}
