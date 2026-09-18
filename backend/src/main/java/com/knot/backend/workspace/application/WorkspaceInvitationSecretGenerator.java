package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationSecrets;

public interface WorkspaceInvitationSecretGenerator {

    WorkspaceInvitationSecrets generate();
}
