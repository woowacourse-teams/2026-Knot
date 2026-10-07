package com.knot.backend.document.domain;

import java.time.Instant;

public enum MyConfirmationState {
    PENDING,
    CONFIRMED,
    NOT_REQUIRED;

    public static MyConfirmationState resolve(
            boolean required,
            Instant confirmedAt
    ) {
        if (!required) {
            return NOT_REQUIRED;
        }
        if (confirmedAt == null) {
            return PENDING;
        }
        return CONFIRMED;
    }
}
