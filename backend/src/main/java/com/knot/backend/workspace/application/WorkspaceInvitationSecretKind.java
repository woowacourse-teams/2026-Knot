package com.knot.backend.workspace.application;

public enum WorkspaceInvitationSecretKind {
    LINK_TOKEN("link-token"),
    INVITE_CODE("invite-code");

    private final String context;

    WorkspaceInvitationSecretKind(String context) {
        this.context = context;
    }

    public String context() {
        return context;
    }
}
