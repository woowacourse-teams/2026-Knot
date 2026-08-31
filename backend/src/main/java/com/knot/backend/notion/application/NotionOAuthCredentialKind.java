package com.knot.backend.notion.application;

public enum NotionOAuthCredentialKind {
    ACCESS_TOKEN("access-token"),
    REFRESH_TOKEN("refresh-token");

    private final String context;

    NotionOAuthCredentialKind(String context) {
        this.context = context;
    }

    public String context() {
        return context;
    }
}
