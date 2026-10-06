package com.knot.backend.document.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentConfirmationState {
    CONFIRMED(0),
    PENDING(1),
    EXCLUDED(2);

    private final int sortOrder;

    public static DocumentConfirmationState resolve(
            boolean activeMember,
            Instant confirmedAt
    ) {
        if (confirmedAt != null) {
            return CONFIRMED;
        }
        if (activeMember) {
            return PENDING;
        }
        return EXCLUDED;
    }
}
