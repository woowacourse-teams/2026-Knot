package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkspaceInvitationTransactionExecutor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkspaceInvitationResult execute(Supplier<WorkspaceInvitationResult> operation) {
        return operation.get();
    }
}
