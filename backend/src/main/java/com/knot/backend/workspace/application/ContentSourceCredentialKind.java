package com.knot.backend.workspace.application;

public enum ContentSourceCredentialKind {
    ACCESS_CREDENTIAL("access-credential"),
    REFRESH_CREDENTIAL("refresh-credential");

    private final String context;

    ContentSourceCredentialKind(String context) {
        this.context = context;
    }

    public String context() {
        return context;
    }
}
