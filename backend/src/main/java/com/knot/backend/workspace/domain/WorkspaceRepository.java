package com.knot.backend.workspace.domain;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository {

    Workspace save(Workspace workspace);

    Optional<Workspace> findById(Long workspaceId);

    Optional<Workspace> findByIdForUpdate(Long workspaceId);

    List<Workspace> findAllByMemberId(Long memberId);
}
