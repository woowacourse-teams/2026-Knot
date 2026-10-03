package com.knot.backend.workspace.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceInvitationFeatures {
    private final boolean multipleEnabled;

    public WorkspaceInvitationFeatures(
            @Value("${workspace.invitation.multiple-enabled:false}") boolean multipleEnabled
    ) {
        this.multipleEnabled = multipleEnabled;
    }

    public boolean multipleEnabled() {
        return multipleEnabled;
    }
}
