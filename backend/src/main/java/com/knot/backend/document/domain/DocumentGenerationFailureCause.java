package com.knot.backend.document.domain;

public enum DocumentGenerationFailureCause {

    TIMEOUT,
    RATE_LIMITED,
    UNAVAILABLE,
    AUTHENTICATION,
    INVALID_INPUT,
    INVALID_RESPONSE,
    INPUT_LIMIT,
    OUTPUT_LIMIT,
    EXECUTION_EXPIRED,
    WORKSPACE_DELETED,
    STORAGE,
    INTERNAL,
    LEGACY_FAILURE
}
