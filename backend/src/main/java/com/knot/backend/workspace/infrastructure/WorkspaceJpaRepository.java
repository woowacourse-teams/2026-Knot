package com.knot.backend.workspace.infrastructure;

import com.knot.backend.workspace.domain.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceJpaRepository extends JpaRepository<Workspace, Long> {}
