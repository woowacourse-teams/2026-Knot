package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceOwnershipTransferCandidateResult;
import java.util.List;

public interface WorkspaceOwnershipTransferCandidateQuery {

    List<WorkspaceOwnershipTransferCandidateResult> findAll(
            Long workspaceId,
            long ownerMemberId
    );
}
