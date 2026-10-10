package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.application.WorkspaceDetailQuery;
import com.knot.backend.workspace.application.dto.result.WorkspaceDetailSnapshot;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WorkspaceDetailQueryAdapter implements WorkspaceDetailQuery {
    private final WorkspaceJpaRepository workspaceJpaRepository;

    @Override
    public Optional<WorkspaceDetailSnapshot> find(
            long workspaceId,
            long memberId
    ) {
        return workspaceJpaRepository.findActiveDetail(
                workspaceId,
                memberId
        )
                .map(
                        row -> new WorkspaceDetailSnapshot(
                                row.name(),
                                toRole(row.myRole()),
                                row.activeMemberCount()
                        )
                );
    }

    private WorkspaceMemberRole toRole(String role) {
        if (role == null) {
            return null;
        }
        return WorkspaceMemberRole.valueOf(role);
    }
}
